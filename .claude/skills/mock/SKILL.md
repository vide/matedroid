---
name: mock
description: Start the Teslamate API mock server to test car image rendering with different vehicle configurations. Takes a description of the car to mock.
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
5. Tell the user that the mock server is running and they should configure the app to use `http://<local_ip>:4002` as the API URL

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
