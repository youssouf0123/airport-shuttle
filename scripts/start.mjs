import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readFileSync, mkdirSync, openSync, writeFileSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const env={...process.env};const path=new URL('../.env',import.meta.url);
if(existsSync(path))for(const line of readFileSync(path,'utf8').split('\n')){const match=line.match(/^([A-Z_]+)=(.*)$/);if(match&&env[match[1]]===undefined)env[match[1]]=match[2]}
if(!env.DB_PASSWORD){console.error('DB_PASSWORD is required. Run node scripts/setup-local.mjs first.');process.exit(1)}
const jar=fileURLToPath(new URL('../airport-shuttle-ws/target/airport-shuttle-ws-1.0.0.jar',import.meta.url));
if(!existsSync(jar)){console.error('Run npm run build first.');process.exit(1)}
const background=process.argv.includes('--background');
const runtime=new URL('../.runtime/',import.meta.url);const pidFile=new URL('server.pid',runtime);
const runtimeJar=fileURLToPath(new URL('airport-shuttle-ws-1.0.0.jar',runtime));
if(background&&existsSync(pidFile)){const pid=Number(readFileSync(pidFile,'utf8'));const command=spawnSync('ps',['-p',String(pid),'-o','command='],{encoding:'utf8'}).stdout;if(command?.includes(runtimeJar)){console.error('Airport Shuttle is already running.');process.exit(1)}}
if(background)mkdirSync(runtime,{recursive:true});
if(background)copyFileSync(jar,runtimeJar);
const log=background?openSync(new URL('server.log',runtime),'a',0o600):undefined;
const child=spawn('java',['-Xms64m','-Xmx384m','-jar',background?runtimeJar:jar],{cwd:new URL('../airport-shuttle-ws/',import.meta.url),stdio:background?['ignore',log,log]:'inherit',env,detached:background});
if(background){
 writeFileSync(pidFile,String(child.pid),{mode:0o600});child.unref();
 let healthy=false;
 for(let i=0;i<30;i++){try{if((await fetch('http://localhost:'+(env.PORT||'8088')+'/api/health',{signal:AbortSignal.timeout(1000)})).ok){healthy=true;break}}catch{}await new Promise(resolve=>setTimeout(resolve,500))}
 if(!healthy){console.error('Server did not become healthy. Check .runtime/server.log.');process.exit(1)}
 console.log('Airport Shuttle is running at http://localhost:'+(env.PORT||'8088')+'/passenger/');process.exit(0);
}
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>child.kill(signal));
child.on('exit',code=>process.exit(code||0));
