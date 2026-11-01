import { readFileSync,existsSync,unlinkSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const file=new URL('../.runtime/server.pid',import.meta.url);const jar=fileURLToPath(new URL('../.runtime/airport-shuttle-ws-1.0.0.jar',import.meta.url));
if(!existsSync(file)){console.log('No managed Airport Shuttle server is running.');process.exit(0)}
const pid=Number(readFileSync(file,'utf8'));const command=spawnSync('ps',['-p',String(pid),'-o','command='],{encoding:'utf8'}).stdout;
if(command?.includes(jar)){process.kill(pid,'SIGTERM');for(let i=0;i<40;i++){try{process.kill(pid,0)}catch{break}await new Promise(resolve=>setTimeout(resolve,250))}}
unlinkSync(file);console.log('Airport Shuttle stopped.');
