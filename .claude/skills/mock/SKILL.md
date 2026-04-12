---
name: mock
description: Start the Teslamate API mock server to test car image rendering with different vehicle configurations. Takes a description of the car to mock. Can also simulate charging sessions (AC/DC).
allowed-tools: Read, Bash
---

# Mock Server

Start the Teslamate API mock server to test car image rendering with different vehicle configurations.

## Usage

The skill takes a description of the car model to mock as an argument. It will find the closest matching profile in `mockserver/cars.json` and start the server on port 4002.

Example arguments:
- `silver model y legacy apollo`
- `white model 3 highland`
- `grey juniper performance`
- `model y juniper white 19`

## Instructions

1. Read the `.env` file to get `TESLAMATE_API_URL` as the upstream URL
2. Read `mockserver/cars.json` to list available profiles
3. Match the user's description to a profile name (partial matching is fine)
4. Start the mock server with:
   ```bash
   cd /home/vide/git/matedroid/mockserver && ./server.py -u <upstream_url> -c <profile_name> -p 4002
   ```
5. Point the app to the mock server via ADB broadcast:
   ```bash
   adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
     -a com.matedroid.SET_ENDPOINT --es url "http://<local_ip>:4002"
   ```
   Get `<local_ip>` from `hostname -I | awk '{print $1}'`.
6. Tell the user the mock server is running and the app has been pointed to it

## Charging Simulation

The mock server supports simulating active charge sessions. Add these flags when the user asks to simulate charging:

- `--charging` — enable charge simulation
- `--charging-dc` — simulate DC fast charging (default is AC 3-phase)
- `--charging-start-soc PCT` — starting battery % (default: 40)
- `--charging-limit-soc PCT` — target battery % (default: 80)
- `--charging-power KW` — charger power in kW (default: 11 AC, 150 DC)

Example:
```bash
cd /home/vide/git/matedroid/mockserver && ./server.py -u <upstream_url> -c <profile_name> -p 4002 \
  --charging --charging-dc --charging-start-soc 20 --charging-limit-soc 80
```

## Restoring the Real Server

When done testing, point the app back to the real API:
```bash
adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
  -a com.matedroid.SET_ENDPOINT --es url "<TESLAMATE_API_URL from .env>"
```

## Available Profiles

Profiles follow naming convention: `model[3|y|s|x]_[variant]_[color]_[wheel]`

Common variants:
- Model 3: `legacy`, `highland`, `highland_perf`
- Model Y: `legacy`, `juniper`, `juniper_perf`
- Model S/X: `plaid`, `100d`

Common colors: `white`, `black`, `silver`, `grey`, `blue`, `red`, `diamond`, `quicksilver`

To list all profiles, run:
```bash
cd /home/vide/git/matedroid/mockserver && ./server.py --list-cars -u http://localhost -c dummy
```
