import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
const env={...process.env};
for(const line of readFileSync(new URL('../.env',import.meta.url),'utf8').split('\n')){const m=line.match(/^([A-Z_]+)=(.*)$/);if(m&&env[m[1]]===undefined)env[m[1]]=m[2]}
const result=spawnSync('mysql',['-h','127.0.0.1','-u',env.DB_USER||'airport_shuttle','airport_shuttle_test'],{env:{...process.env,MYSQL_PWD:env.DB_PASSWORD},input:'DELETE FROM push_deliveries; DELETE FROM push_subscriptions; DELETE FROM notifications; DELETE FROM trip_events; DELETE FROM trips;',encoding:'utf8'});
if(result.status!==0){console.error(result.stderr);process.exit(1)}
console.log('Cleared workflow fixtures in airport_shuttle_test only.');
