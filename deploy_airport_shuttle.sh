#!/bin/bash
set -euo pipefail

# Production deployment: build on the MacBook Pro, run the full stack on the iMac.
# The iMac hosts Spring Boot, MySQL, and NGINX. Existing transportation sites are
# preserved; Airport Shuttle gets its own hostname and NGINX server block.

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

IMAC_HOST="${IMAC_HOST:-10.0.0.26}"
IMAC_PORT="${IMAC_PORT:-22}"
IMAC_LOGIN_FILE="${IMAC_LOGIN_FILE:-$HOME/imac.login}"
IMAC_PASSWORD="${IMAC_PASSWORD:-}"
REMOTE_ROOT="${REMOTE_ROOT:-/Users/youssoufdiarra/airport-shuttle}"
NGINX_ROOT="${NGINX_ROOT:-/usr/local/etc/nginx/airport-shuttle}"
NGINX_CONF="${NGINX_CONF:-/usr/local/etc/nginx/conf/airport-shuttle.conf}"
SERVER_NAME="${SERVER_NAME:-managetransportation.com}"
BACKEND_PORT="${BACKEND_PORT:-8088}"
JAVA_BIN="${JAVA_BIN:-/Users/youssoufdiarra/tms/runtime/jdk-17.0.19+10/Contents/Home/bin/java}"
# Keep Airport Shuttle isolated from the existing TMS database. Set this only
# for an intentional shared-database test; production defaults to its own DB.
USE_EXISTING_TMS_DB="${USE_EXISTING_TMS_DB:-false}"
TMS_DATABASE_NAME="${TMS_DATABASE_NAME:-tms}"
MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"
MYSQL_ADMIN_PASSWORD="${MYSQL_ADMIN_PASSWORD:-}"

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

DB_NAME="${DB_NAME:-airport_shuttle}"
DB_USER="${DB_USER:-airport_shuttle}"
DB_PASSWORD="${DB_PASSWORD:-}"
SEED_DEMO="${SEED_DEMO:-false}"
DEMO_PASSWORD="${DEMO_PASSWORD:-}"

if [ -z "$IMAC_PASSWORD" ] && [ -f "$IMAC_LOGIN_FILE" ]; then
  IMAC_PASSWORD="$(awk -F'is:' '/The password is:/ {gsub(/^[ \t]+|[ \t]+$/, "", $2); print $2; exit}' "$IMAC_LOGIN_FILE")"
fi

if [ "$USE_EXISTING_TMS_DB" != "true" ] && [ -z "$MYSQL_ADMIN_PASSWORD" ] && [ -t 0 ]; then
  read -r -s -p "iMac MySQL administrator password: " MYSQL_ADMIN_PASSWORD
  printf '\n'
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
if [ "$USE_EXISTING_TMS_DB" != "true" ] && [ -z "$DB_PASSWORD" ]; then
  echo "DB_PASSWORD is required in .env or the environment." >&2
  exit 1
fi
if [ "$USE_EXISTING_TMS_DB" != "true" ] && [ -z "$MYSQL_ADMIN_PASSWORD" ]; then
  echo "MYSQL_ADMIN_PASSWORD is required to provision the iMac MySQL database." >&2
  echo "It is intentionally not stored in the repository." >&2
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

shell_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\''/g")"
}

sql_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

echo "Building Airport Shuttle locally..."
npm run build

STAGING="$(mktemp -d /tmp/airport-shuttle-deploy.XXXXXX)"
cleanup() { rm -rf "$STAGING"; }
trap cleanup EXIT

for role in passenger dispatcher driver; do
  tar -C "dist/$role" -czf "$STAGING/$role.tgz" .
done

BACKEND_JAR="airport-shuttle-ws/target/airport-shuttle-ws-1.0.0.jar"
if [ ! -f "$BACKEND_JAR" ]; then
  echo "Backend jar was not produced: $BACKEND_JAR" >&2
  exit 1
fi

cat > "$STAGING/backend.env" <<EOF
DB_NAME=$(shell_quote "$DB_NAME")
DB_USER=$(shell_quote "$DB_USER")
DB_PASSWORD=$(shell_quote "$DB_PASSWORD")
PORT=$(shell_quote "$BACKEND_PORT")
BIND_ADDRESS='127.0.0.1'
SEED_DEMO=$(shell_quote "$SEED_DEMO")
DEMO_PASSWORD=$(shell_quote "$DEMO_PASSWORD")
EOF

cat > "$STAGING/start-backend.sh" <<EOF
#!/bin/bash
set -euo pipefail
APP_DIR=$(shell_quote "$REMOTE_ROOT/backend")
JAVA_BIN=$(shell_quote "$JAVA_BIN")
PID_FILE="\$APP_DIR/backend.pid"
set -a
. "\$APP_DIR/backend.env"
set +a
if [ -f "\$PID_FILE" ] && kill -0 "\$(cat "\$PID_FILE")" 2>/dev/null; then
  kill "\$(cat "\$PID_FILE")" || true
  sleep 2
fi
cd "\$APP_DIR"
nohup "\$JAVA_BIN" -Xms64m -Xmx384m -jar "\$APP_DIR/airport-shuttle-ws.jar" > "\$APP_DIR/backend.log" 2>&1 < /dev/null &
echo "\$!" > "\$PID_FILE"
sleep 5
curl -fsS "http://127.0.0.1:\${PORT}/api/health" >/dev/null
echo "Airport Shuttle backend started on port \${PORT}"
EOF
chmod 700 "$STAGING/start-backend.sh"

cat > "$STAGING/prepare-existing-db-env.sh" <<EOF
#!/bin/bash
set -euo pipefail
APP_DIR=$(shell_quote "$REMOTE_ROOT/backend")
TMS_DATABASE_NAME=$(shell_quote "$TMS_DATABASE_NAME")
pid="\$(pgrep -f 'tms.jar' | head -1 || true)"
if [ -z "\$pid" ]; then
  echo "The existing TMS backend is not running; cannot reuse its MySQL credentials." >&2
  exit 1
fi
read_env() {
  ps eww "\$pid" | tr ' ' '\\n' | awk -F= -v key="\$1" '\$1 == key {print substr(\$0, index(\$0, "=") + 1); exit}'
}
db_user="\$(read_env SPRING_DATASOURCE_USERNAME)"
db_password="\$(read_env SPRING_DATASOURCE_PASSWORD)"
if [ -z "\$db_user" ] || [ -z "\$db_password" ]; then
  echo "The existing TMS backend did not expose MySQL credentials." >&2
  exit 1
fi
printf 'DB_URL=%q\\n' "jdbc:mysql://127.0.0.1:3306/\$TMS_DATABASE_NAME?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" > "\$APP_DIR/backend.env"
printf 'DB_USER=%q\\n' "\$db_user" >> "\$APP_DIR/backend.env"
printf 'DB_PASSWORD=%q\\n' "\$db_password" >> "\$APP_DIR/backend.env"
printf 'PORT=%q\\n' '$BACKEND_PORT' >> "\$APP_DIR/backend.env"
printf 'BIND_ADDRESS=%q\\n' '127.0.0.1' >> "\$APP_DIR/backend.env"
printf 'SEED_DEMO=%q\\n' '$SEED_DEMO' >> "\$APP_DIR/backend.env"
printf 'DEMO_PASSWORD=%q\\n' '$DEMO_PASSWORD' >> "\$APP_DIR/backend.env"
chmod 600 "\$APP_DIR/backend.env"
EOF
chmod 700 "$STAGING/prepare-existing-db-env.sh"

cat > "$STAGING/db-setup.sql" <<EOF
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS $(sql_quote "$DB_USER")@'localhost' IDENTIFIED BY $(sql_quote "$DB_PASSWORD");
ALTER USER $(sql_quote "$DB_USER")@'localhost' IDENTIFIED BY $(sql_quote "$DB_PASSWORD");
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO $(sql_quote "$DB_USER")@'localhost';
FLUSH PRIVILEGES;
EOF

cat > "$STAGING/airport-shuttle.conf" <<EOF
server {
    listen 80;
    listen [::]:80;
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name $SERVER_NAME www.$SERVER_NAME;

    ssl_certificate /usr/local/etc/nginx/cert.pem;
    ssl_certificate_key /usr/local/etc/nginx/cert.key;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;

    location = / {
        return 302 /passenger/;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:$BACKEND_PORT;
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

    location / {
        root $NGINX_ROOT/passenger;
        try_files \$uri \$uri/ /passenger/index.html;
    }
}
EOF

cat > "$STAGING/replace-root-server.awk" <<'AWK'
function brace_delta(s, opens, closes) {
    opens = gsub(/\{/, "", s)
    closes = gsub(/\}/, "", s)
    return opens - closes
}

BEGIN {
    in_server = 0
    depth = 0
    skip = 0
    buffer = ""
}

{
    if (!in_server && $0 ~ /^[[:space:]]*server[[:space:]]*\{/) {
        in_server = 1
        depth = 1
        skip = 0
        buffer = $0 "\n"
        next
    }
    if (in_server) {
        buffer = buffer $0 "\n"
        if ($0 ~ /server_name[[:space:]]+/ && $0 ~ target) skip = 1
        depth += brace_delta($0)
        if (depth == 0) {
            if (!skip) printf "%s", buffer
            in_server = 0
            buffer = ""
        }
        next
    }
    print
}
AWK

cat > "$STAGING/insert-nginx-include.awk" <<'AWK'
BEGIN { inserted = 0 }
{
    if (!inserted && $0 ~ /^http[[:space:]]*\{/) {
        print
        print "    include " include ";"
        inserted = 1
        next
    }
    print
}
AWK

echo "Preparing iMac application directories..."
run_ssh "mkdir -p '$REMOTE_ROOT/backend' '$REMOTE_ROOT/frontend' '$REMOTE_ROOT/db'"
run_scp "$BACKEND_JAR" "$REMOTE_ROOT/backend/airport-shuttle-ws.jar"
run_scp "$STAGING/start-backend.sh" "$REMOTE_ROOT/backend/start-backend.sh"
if [ "$USE_EXISTING_TMS_DB" = "true" ]; then
  run_scp "$STAGING/prepare-existing-db-env.sh" "$REMOTE_ROOT/backend/prepare-existing-db-env.sh"
else
  run_scp "$STAGING/backend.env" "$REMOTE_ROOT/backend/backend.env"
  run_scp "$STAGING/db-setup.sql" "$REMOTE_ROOT/db/db-setup.sql"
fi
run_scp "$STAGING/airport-shuttle.conf" "$REMOTE_ROOT/airport-shuttle.conf"
run_scp "$STAGING/replace-root-server.awk" "$REMOTE_ROOT/replace-root-server.awk"
run_scp "$STAGING/insert-nginx-include.awk" "$REMOTE_ROOT/insert-nginx-include.awk"
for role in passenger dispatcher driver; do
  run_scp "$STAGING/$role.tgz" "$REMOTE_ROOT/frontend/$role.tgz"
done

if [ "$USE_EXISTING_TMS_DB" = "true" ]; then
  echo "Reusing the existing iMac TMS MySQL database and account..."
  DB_PREPARE_CMD="chmod 700 '$REMOTE_ROOT/backend/prepare-existing-db-env.sh'; '$REMOTE_ROOT/backend/prepare-existing-db-env.sh'"
else
  MYSQL_ADMIN_PASSWORD_Q="$(shell_quote "$MYSQL_ADMIN_PASSWORD")"
  echo "Provisioning iMac MySQL database and user..."
  DB_PREPARE_CMD="MYSQL_PWD=$MYSQL_ADMIN_PASSWORD_Q /usr/local/mysql/bin/mysql -u $(shell_quote "$MYSQL_ADMIN_USER") < '$REMOTE_ROOT/db/db-setup.sql'"
fi

echo "Installing backend and web interfaces on the iMac..."
run_ssh "set -e; $DB_PREPARE_CMD; chmod 700 '$REMOTE_ROOT/backend/start-backend.sh'; '$REMOTE_ROOT/backend/start-backend.sh'; sudo mkdir -p '$NGINX_ROOT'/passenger '$NGINX_ROOT'/dispatcher '$NGINX_ROOT'/driver; for role in passenger dispatcher driver; do sudo find '$NGINX_ROOT/'\$role -mindepth 1 -maxdepth 1 -exec rm -rf {} +; sudo tar -C '$NGINX_ROOT/'\$role -xzf '$REMOTE_ROOT/frontend/'\$role.tgz; done; sudo mkdir -p '$(dirname "$NGINX_CONF")'; sudo cp '$REMOTE_ROOT/airport-shuttle.conf' '$NGINX_CONF'; awk -v target='$SERVER_NAME' -f '$REMOTE_ROOT/replace-root-server.awk' /usr/local/etc/nginx/nginx.conf > '$REMOTE_ROOT/nginx.conf.tmp'; sudo cp '$REMOTE_ROOT/nginx.conf.tmp' /usr/local/etc/nginx/nginx.conf; if ! grep -Fq '$NGINX_CONF' /usr/local/etc/nginx/nginx.conf; then awk -v include='$NGINX_CONF' -f '$REMOTE_ROOT/insert-nginx-include.awk' /usr/local/etc/nginx/nginx.conf > '$REMOTE_ROOT/nginx.conf.tmp'; sudo cp '$REMOTE_ROOT/nginx.conf.tmp' /usr/local/etc/nginx/nginx.conf; fi; sudo /usr/local/etc/nginx/sbin/nginx -t -c /usr/local/etc/nginx/nginx.conf; sudo /usr/local/etc/nginx/sbin/nginx -s reload -c /usr/local/etc/nginx/nginx.conf"

echo "Airport Shuttle production deployment completed on the iMac."
echo "Backend: http://$SERVER_NAME/api/health"
echo "Passenger: http://$SERVER_NAME/passenger/"
echo "Dispatcher: http://$SERVER_NAME/dispatcher/"
echo "Driver: http://$SERVER_NAME/driver/"
echo "The existing $SERVER_NAME root site was replaced as requested."
