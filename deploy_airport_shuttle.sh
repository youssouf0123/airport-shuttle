#!/bin/bash
set -euo pipefail

# Deploy the three web frontends from this MacBook to the iMac NGINX mirror.
# The Spring Boot backend and MySQL database remain on this MacBook.
IMAC_HOST="${IMAC_HOST:-10.0.0.26}"
IMAC_PORT="${IMAC_PORT:-22}"
IMAC_LOGIN_FILE="${IMAC_LOGIN_FILE:-$HOME/imac.login}"
IMAC_PASSWORD="${IMAC_PASSWORD:-}"
REMOTE_ROOT="${REMOTE_ROOT:-/Users/youssoufdiarra/airport-shuttle}"
NGINX_ROOT="${NGINX_ROOT:-/usr/local/etc/nginx/airport-shuttle}"
NGINX_CONF="${NGINX_CONF:-/usr/local/etc/nginx/conf/airport-shuttle.conf}"
SERVER_NAME="${SERVER_NAME:-_}"
MACBOOK_API_URL="${MACBOOK_API_URL:-http://$(ipconfig getifaddr en0 2>/dev/null || printf '127.0.0.1'):8088}"
DEPLOY_FIREBASE="${DEPLOY_FIREBASE:-false}"
FIREBASE_PROJECT="${FIREBASE_PROJECT:-}"

if [ -z "$IMAC_PASSWORD" ] && [ -f "$IMAC_LOGIN_FILE" ]; then
  IMAC_PASSWORD="$(awk -F'is:' '/The password is:/ {gsub(/^[ \t]+|[ \t]+$/, "", $2); print $2; exit}' "$IMAC_LOGIN_FILE")"
fi

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

require_command npm
require_command tar
require_command expect

if [ -z "$IMAC_PASSWORD" ]; then
  echo "IMAC_PASSWORD is required, or provide $IMAC_LOGIN_FILE." >&2
  exit 1
fi

run_ssh() {
  local remote_command="$1"
  IMAC_PASSWORD="$IMAC_PASSWORD" expect -f - "$IMAC_PORT" "$IMAC_HOST" "$remote_command" <<'EOF'
set timeout -1
set port [lindex $argv 0]
set host [lindex $argv 1]
set remote_command [lindex $argv 2]
spawn ssh -tt -p $port -o StrictHostKeyChecking=accept-new $host $remote_command
expect {
  -re {[Pp]assword:} {send -- "$env(IMAC_PASSWORD)\r"; exp_continue}
  eof {catch wait result; exit [lindex $result 3]}
}
EOF
}

run_scp() {
  local source_path="$1"
  local target_path="$2"
  IMAC_PASSWORD="$IMAC_PASSWORD" expect -f - "$IMAC_PORT" "$source_path" "$IMAC_HOST:$target_path" <<'EOF'
set timeout -1
set port [lindex $argv 0]
set source_path [lindex $argv 1]
set target_path [lindex $argv 2]
spawn scp -P $port -o StrictHostKeyChecking=accept-new $source_path $target_path
expect {
  -re {[Pp]assword:} {send -- "$env(IMAC_PASSWORD)\r"; exp_continue}
  eof {catch wait result; exit [lindex $result 3]}
}
EOF
}

echo "Building Airport Shuttle frontends locally..."
npm run build

STAGING="$(mktemp -d /tmp/airport-shuttle-deploy.XXXXXX)"
cleanup() { rm -rf "$STAGING"; }
trap cleanup EXIT

for role in passenger dispatcher driver; do
  tar -C "dist/$role" -czf "$STAGING/$role.tgz" .
done

cat > "$STAGING/airport-shuttle.conf" <<EOF
server {
    listen 80;
    server_name $SERVER_NAME;

    location /api/ {
        proxy_pass $MACBOOK_API_URL;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location /passenger/ {
        alias $NGINX_ROOT/passenger/;
        try_files \$uri \$uri/ /passenger/index.html;
    }

    location /dispatcher/ {
        alias $NGINX_ROOT/dispatcher/;
        try_files \$uri \$uri/ /dispatcher/index.html;
    }

    location /driver/ {
        alias $NGINX_ROOT/driver/;
        try_files \$uri \$uri/ /driver/index.html;
    }
}
EOF

echo "Preparing iMac NGINX mirror at $IMAC_HOST..."
run_ssh "mkdir -p '$REMOTE_ROOT' '$REMOTE_ROOT/frontend'"
for role in passenger dispatcher driver; do
  run_scp "$STAGING/$role.tgz" "$REMOTE_ROOT/frontend/$role.tgz"
done
run_scp "$STAGING/airport-shuttle.conf" "$REMOTE_ROOT/airport-shuttle.conf"

run_ssh "set -e; mkdir -p '$REMOTE_ROOT/frontend'; sudo mkdir -p '$NGINX_ROOT'/passenger '$NGINX_ROOT'/dispatcher '$NGINX_ROOT'/driver; for role in passenger dispatcher driver; do sudo find '$NGINX_ROOT/'\$role -mindepth 1 -maxdepth 1 -exec rm -rf {} +; sudo tar -C '$NGINX_ROOT/'\$role -xzf '$REMOTE_ROOT/frontend/'\$role.tgz; done; sudo cp '$REMOTE_ROOT/airport-shuttle.conf' '$NGINX_CONF'; if ! grep -Fq '$NGINX_CONF' /usr/local/etc/nginx/nginx.conf; then sudo sed -i '' '/^http {/a\\    include $NGINX_CONF;' /usr/local/etc/nginx/nginx.conf; fi; sudo '$NGINX_ROOT/../sbin/nginx' -t -c /usr/local/etc/nginx/nginx.conf; sudo '$NGINX_ROOT/../sbin/nginx' -s reload -c /usr/local/etc/nginx/nginx.conf"

if [ "$DEPLOY_FIREBASE" = "true" ]; then
  if [ -z "$FIREBASE_PROJECT" ]; then
    echo "FIREBASE_PROJECT is required when DEPLOY_FIREBASE=true." >&2
    exit 1
  fi
  echo "Firebase deployment is enabled, but it requires a configured Airport Shuttle Firebase project and public API URL."
  echo "The iMac NGINX mirror was deployed; Firebase was not changed by this script."
else
  echo "Firebase deployment skipped. Set DEPLOY_FIREBASE=true after configuring the Airport Shuttle Firebase project."
fi

echo "Airport Shuttle frontend deployment complete."
echo "Backend remains on this MacBook at $MACBOOK_API_URL"
