import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
const env={...process.env,MAVEN_OPTS:'-Xmx512m'};
for(const line of readFileSync(new URL('../.env',import.meta.url),'utf8').split('\n')){const m=line.match(/^([A-Z_]+)=(.*)$/);if(m&&env[m[1]]===undefined)env[m[1]]=m[2]}
const r=spawnSync('mvn',['test'],{cwd:new URL('../airport-shuttle-ws/',import.meta.url),env,stdio:'inherit'});process.exit(r.status||0);
