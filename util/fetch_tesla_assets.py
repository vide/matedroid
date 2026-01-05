#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["httpx", "rich"]
# ///
"""
Tesla Car Image Asset Fetcher

Downloads Tesla car 3D renders from the Tesla compositor service
for use in the MateDroid Android app.

Supports both legacy (pre-2024) and new (Highland/Juniper) models using
different compositor endpoints.

Both endpoints support transparent PNG output via bkba_opt=1 parameter:
- bkba_opt=1: Transparent PNG (used for both compositors)
- bkba_opt=2: Opaque JPEG with background

Usage:
    ./fetch_tesla_assets.py [--output-dir PATH] [--dry-run]
"""

import argparse
import asyncio
import sys
from pathlib import Path

import httpx
from rich.console import Console
from rich.progress import Progress, TaskID

console = Console()

# Old Compositor (Legacy Models: pre-2024 Model 3, pre-2025 Model Y)
OLD_COMPOSITOR_URL = "https://static-assets.tesla.com/v1/compositor/"
OLD_VIEW = "STUD_3QTR"
OLD_SIZE = 800
OLD_BKBA_OPT = 1  # Transparent background

# New Compositor (Highland Model 3 2024+, Juniper Model Y 2025+)
NEW_COMPOSITOR_URL = "https://static-assets.tesla.com/configurator/compositor"
NEW_VIEW = "STUD_3QTR"  # Use same view as old compositor for consistent sizing
NEW_SIZE = 800
NEW_BKBA_OPT = 1  # Transparent background (1=PNG transparent, 2=JPEG opaque)
NEW_CONTEXT = "design_studio_2"

# Legacy Model 3 (pre-2024)
LEGACY_M3 = {
    "model_code": "m3",
    "file_prefix": "m3",
    "name": "Model 3 (Legacy)",
    "colors": ["PBSB", "PMNG", "PMSS", "PPSW", "PPSB", "PPMR", "PMBL"],
    "wheels": ["W38B", "W39B", "W32P"],
    "compositor": "old",
}

# Highland Model 3 (2024+)
# MT369 = Standard, MT370 = Premium, MT371 = Performance
# Using MT370 (Premium) as default - most common
HIGHLAND_M3 = {
    "model_code": "m3",
    "file_prefix": "m3h",  # h = highland
    "name": "Model 3 Highland",
    "trim_code": "MT370",
    "interior_code": "IPB3",
    "colors": ["PBSB", "PPSW", "PPSB", "PN00", "PN01", "PR01", "PX02"],
    "wheels": ["W38A"],  # 18" Photon wheels
    "compositor": "new",
}

# Highland Performance (different wheel)
HIGHLAND_M3_PERF = {
    "model_code": "m3",
    "file_prefix": "m3hp",  # hp = highland performance
    "name": "Model 3 Highland Performance",
    "trim_code": "MT371",
    "interior_code": "IPB4",
    "colors": ["PBSB", "PPSW", "PPSB", "PN00", "PN01", "PR01", "PX02"],
    "wheels": ["W30P"],  # 20" Performance wheels
    "compositor": "new",
}

# Legacy Model Y (pre-2025)
LEGACY_MY = {
    "model_code": "my",
    "file_prefix": "my",
    "name": "Model Y (Legacy)",
    "colors": ["PBSB", "PMNG", "PPSW", "PPSB", "PPMR"],
    "wheels": ["WY18B", "WY19B", "WY20P", "WY0S", "WY1S"],
    "compositor": "old",
}

# Juniper Model Y (2025+)
# MTY68 = Standard/Long Range configuration (18"/19" wheels)
# MTY60 = Premium configuration with 20" Helix wheels
JUNIPER_MY = {
    "model_code": "my",
    "file_prefix": "myj",  # j = juniper
    "name": "Model Y Juniper",
    "trim_code": "MTY68",
    "interior_code": "IBB3",
    "colors": ["PPSW", "PN01", "PX02"],
    "wheels": ["WY18P", "WY19P"],  # WY18P = Photon 18" (Standard), WY19P = Crossflow 19" (Premium)
    "compositor": "new",
}

# Juniper Model Y Premium (2025+)
# MTY60 supports more colors than MTY68 for 19"/20" wheels
JUNIPER_MY_PREMIUM = {
    "model_code": "my",
    "file_prefix": "myj",  # Same prefix, different wheel
    "name": "Model Y Juniper Premium",
    "trim_code": "MTY60",
    "interior_code": "IPB8",
    "colors": ["PPSW", "PN01", "PX02", "PN00", "PR01", "PPSB"],  # 6 colors
    "wheels": ["WY19P", "WY20A"],  # 19" Crossflow and 20" Helix 2.0
    "compositor": "new",
}

# Juniper Model Y Performance (2025+)
# MTY53 = Performance configuration with red calipers
JUNIPER_MY_PERF = {
    "model_code": "my",
    "file_prefix": "myjp",  # jp = juniper performance
    "name": "Model Y Juniper Performance",
    "trim_code": "MTY53",
    "interior_code": "IPB10",
    "colors": ["PPSW", "PN01", "PX02", "PB02", "PN00", "PR01"],  # All 6 colors available
    "wheels": ["WY21A"],  # 21" Überturbine wheels
    "compositor": "new",
}

# Model S (Legacy compositor)
MODEL_S = {
    "model_code": "ms",
    "file_prefix": "ms",
    "name": "Model S",
    "colors": ["PBSB", "PMNG", "PPSW", "PPSB", "PPMR"],
    "wheels": ["WT19"],  # 19" Tempest wheels
    "compositor": "old",
}

# Model X (Legacy compositor)
MODEL_X = {
    "model_code": "mx",
    "file_prefix": "mx",
    "name": "Model X",
    "colors": ["PBSB", "PMNG", "PPSW", "PPSB", "PPMR"],
    "wheels": ["WX20"],  # 20" Cyberstream wheels
    "compositor": "old",
}

# All model configurations
ALL_MODELS = [LEGACY_M3, HIGHLAND_M3, HIGHLAND_M3_PERF, LEGACY_MY, JUNIPER_MY, JUNIPER_MY_PREMIUM, JUNIPER_MY_PERF, MODEL_S, MODEL_X]

# Color name mapping for display
COLOR_NAMES = {
    "PBSB": "Solid Black",
    "PMNG": "Midnight Silver Metallic",
    "PMSS": "Silver Metallic",
    "PPSW": "Pearl White Multi-Coat",
    "PPSB": "Deep Blue Metallic",
    "PPMR": "Red Multi-Coat",
    "PMBL": "Obsidian Black Metallic",
    "PN00": "Quicksilver",
    "PN01": "Stealth Grey",
    "PR00": "Midnight Cherry Red",
    "PR01": "Ultra Red",
    "PX02": "Black Diamond",
    "PB02": "Deep Blue",  # Juniper-only Deep Blue (different from PPSB)
}


def build_old_compositor_url(model: str, color: str, wheel: str) -> str:
    """Build URL for legacy compositor (pre-Highland/Juniper)."""
    options = f"{color},{wheel}"
    return (
        f"{OLD_COMPOSITOR_URL}"
        f"?model={model}"
        f"&view={OLD_VIEW}"
        f"&size={OLD_SIZE}"
        f"&options={options}"
        f"&bkba_opt={OLD_BKBA_OPT}"
    )


def build_new_compositor_url(
    model: str, trim: str, color: str, wheel: str, interior: str
) -> str:
    """Build URL for new compositor (Highland/Juniper)."""
    # New compositor uses $ prefix for option codes
    options = f"${trim},${color},${wheel},${interior}"
    return (
        f"{NEW_COMPOSITOR_URL}"
        f"?context={NEW_CONTEXT}"
        f"&options={options}"
        f"&view={NEW_VIEW}"
        f"&model={model}"
        f"&size={NEW_SIZE}"
        f"&bkba_opt={NEW_BKBA_OPT}"
    )


def get_output_filename(prefix: str, color: str, wheel: str, ext: str) -> str:
    """Generate the output filename for an asset."""
    return f"{prefix}_{color}_{wheel}.{ext}"


async def download_image(
    client: httpx.AsyncClient,
    url: str,
    output_path: Path,
    progress: Progress,
    task_id: TaskID,
    description: str,
    expected_format: str,  # "png" or "jpeg"
    min_valid_size: int = 50000,  # Minimum size to consider valid (for old compositor)
) -> bool:
    """Download a single image from the compositor."""
    try:
        response = await client.get(url, follow_redirects=True)
        response.raise_for_status()

        content = response.content

        # Validate the response
        if expected_format == "png":
            # Old compositor returns PNG
            if not content.startswith(b'\x89PNG'):
                console.print(f"[yellow]Warning: {description} - Not a valid PNG[/yellow]")
                progress.update(task_id, advance=1)
                return False
            # Check for placeholder images (small file size)
            if len(content) < min_valid_size:
                console.print(f"[yellow]Warning: {description} - Placeholder image ({len(content)} bytes)[/yellow]")
                progress.update(task_id, advance=1)
                return False
        else:
            # New compositor returns JPEG
            if not content.startswith(b'\xff\xd8\xff'):
                # Check if it's an error page (HTML)
                if len(content) > 300000:  # Error pages are ~313KB
                    console.print(f"[yellow]Warning: {description} - Error page returned[/yellow]")
                    progress.update(task_id, advance=1)
                    return False
                console.print(f"[yellow]Warning: {description} - Not a valid JPEG[/yellow]")
                progress.update(task_id, advance=1)
                return False

        output_path.write_bytes(content)
        progress.update(task_id, advance=1)
        return True

    except httpx.HTTPStatusError as e:
        console.print(f"[red]Error: {description} - HTTP {e.response.status_code}[/red]")
        progress.update(task_id, advance=1)
        return False
    except Exception as e:
        console.print(f"[red]Error: {description} - {e}[/red]")
        progress.update(task_id, advance=1)
        return False


async def download_all_assets(output_dir: Path, dry_run: bool = False) -> tuple[int, int]:
    """Download all asset combinations."""
    output_dir.mkdir(parents=True, exist_ok=True)

    # Build list of all downloads
    downloads = []

    for model_config in ALL_MODELS:
        for color_code in model_config["colors"]:
            for wheel_code in model_config["wheels"]:
                if model_config["compositor"] == "old":
                    url = build_old_compositor_url(
                        model_config["model_code"], color_code, wheel_code
                    )
                    ext = "png"
                    expected_format = "png"
                else:
                    url = build_new_compositor_url(
                        model_config["model_code"],
                        model_config["trim_code"],
                        color_code,
                        wheel_code,
                        model_config["interior_code"],
                    )
                    ext = "png"  # bkba_opt=1 returns transparent PNG
                    expected_format = "png"

                filename = get_output_filename(
                    model_config["file_prefix"], color_code, wheel_code, ext
                )
                output_path = output_dir / filename
                color_name = COLOR_NAMES.get(color_code, color_code)
                description = f"{model_config['name']} {color_name} {wheel_code}"

                downloads.append((url, output_path, description, expected_format))

    total = len(downloads)
    console.print(f"\n[bold]Tesla Car Asset Fetcher[/bold]")
    console.print(f"Total images to download: {total}")
    console.print(f"Output directory: {output_dir}\n")

    if dry_run:
        console.print("[yellow]Dry run mode - showing what would be downloaded:[/yellow]\n")
        for url, output_path, description, _ in downloads:
            console.print(f"  {description}")
            console.print(f"    -> {output_path.name}")
            console.print(f"    URL: {url}\n")
        return total, 0

    # Download with progress bar
    success_count = 0

    async with httpx.AsyncClient(timeout=30.0) as client:
        with Progress() as progress:
            task_id = progress.add_task("[cyan]Downloading...", total=total)

            # Process in batches to avoid overwhelming the server
            batch_size = 5
            for i in range(0, len(downloads), batch_size):
                batch = downloads[i:i + batch_size]
                tasks = [
                    download_image(
                        client, url, output_path, progress, task_id,
                        description, expected_format
                    )
                    for url, output_path, description, expected_format in batch
                ]
                results = await asyncio.gather(*tasks)
                success_count += sum(results)

                # Small delay between batches
                if i + batch_size < len(downloads):
                    await asyncio.sleep(0.5)

    return total, success_count


def main():
    parser = argparse.ArgumentParser(
        description="Download Tesla car images from compositor service"
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).parent.parent / "app/src/main/assets/car_images",
        help="Output directory for images",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be downloaded without actually downloading",
    )
    args = parser.parse_args()

    total, success = asyncio.run(download_all_assets(args.output_dir, args.dry_run))

    if not args.dry_run:
        console.print(f"\n[bold]Download complete![/bold]")
        console.print(f"  Success: {success}/{total}")
        if success < total:
            console.print(f"  [yellow]Skipped/Failed: {total - success}[/yellow]")

    sys.exit(0)


if __name__ == "__main__":
    main()
