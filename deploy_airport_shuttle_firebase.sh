#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

API_BASE="${AIRPORT_API_BASE:-https://managetransportation.com}"

command -v firebase >/dev/null 2>&1 || { echo "Firebase CLI is required." >&2; exit 1; }

npm run build --prefix passenger-ui
npm run build --prefix dispatcher-ui
npm run build --prefix driver-ui
mkdir -p dist/passenger dist/dispatcher dist/driver
cp -R passenger-ui/dist/browser/. dist/passenger/
cp -R dispatcher-ui/dist/browser/. dist/dispatcher/
cp -R driver-ui/dist/browser/. dist/driver/
cp public/index.html dist/index.html
cp public/sw.js dist/sw.js

for role in passenger dispatcher driver; do
  case "$role" in
    passenger) project=airport-shuttle-passenger-ui ;;
    dispatcher) project=airport-shuttle-dispatcher-ui ;;
    driver) project=airport-shuttle-driver-ui ;;
  esac
  perl -0pi -e 's#<base href="/'"$role"'/">#<base href="/">#' "dist/$role/index.html"
  printf '%s\n' "globalThis.__AIRPORT_API_BASE__ = '$API_BASE';" > "dist/$role/config.js"
  config_file="$(mktemp /tmp/airport-shuttle-firebase.XXXXXX.json)"
  trap 'rm -f "$config_file"' EXIT
  cat > "$config_file" <<EOF
{
  "hosting": {
    "public": "$PROJECT_ROOT/dist/$role",
    "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
    "cleanUrls": true,
    "rewrites": [{"source": "**", "destination": "/index.html"}]
  }
}
EOF
  echo "Deploying $role UI to Firebase project $project..."
  firebase deploy --config "$config_file" --project "$project" --only hosting
  rm -f "$config_file"
  trap - EXIT
done

echo "All three Airport Shuttle Firebase UIs deployed."
