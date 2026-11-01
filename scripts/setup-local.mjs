import { randomBytes } from 'node:crypto';
import { existsSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
const path=new URL('../.env',import.meta.url);
if(existsSync(path)){console.error('.env already exists. No changes made.');process.exit(1)}
const password=randomBytes(24).toString('hex');
const sql=`CREATE DATABASE IF NOT EXISTS airport_shuttle CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'airport_shuttle'@'localhost' IDENTIFIED BY '${password}';
ALTER USER 'airport_shuttle'@'localhost' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON airport_shuttle.* TO 'airport_shuttle'@'localhost';
CREATE DATABASE IF NOT EXISTS airport_shuttle_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON airport_shuttle_test.* TO 'airport_shuttle'@'localhost';`;
const result=spawnSync('mysql',['-u',process.env.MYSQL_ADMIN_USER||'root'],{input:sql,encoding:'utf8'});
if(result.status!==0){console.error(result.stderr);process.exit(1)}
writeFileSync(path,`DB_USER=airport_shuttle\nDB_PASSWORD=${password}\nSEED_DEMO=true\nDEMO_PASSWORD=ShuttleLocal2026!\nPORT=8088\n`,{mode:0o600,flag:'wx'});
console.log('Local application and test databases ready. Credentials written to .env (owner access only).');
