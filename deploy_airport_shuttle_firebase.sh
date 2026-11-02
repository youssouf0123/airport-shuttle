#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

API_BASE="${AIRPORT_API_BASE:-https://managetransportation.com}"

command -v firebase >/dev/null 2>&1 || { echo "Firebase CLI is required." >&2; exit 1; }

npm run build

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
