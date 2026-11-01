import { spawnSync } from 'node:child_process';
import { mkdirSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
for(const role of ['passenger','dispatcher','driver']){
 const result=spawnSync(process.execPath,['node_modules/@angular/cli/bin/ng.js','build',role],{cwd:root,stdio:'inherit',env:{...process.env,NG_BUILD_MAX_WORKERS:'2',NODE_OPTIONS:'--max-old-space-size=1536'}});
 if(result.status!==0)process.exit(result.status||1);
}
mkdirSync(new URL('../dist/',import.meta.url),{recursive:true});
copyFileSync(new URL('../public/sw.js',import.meta.url),new URL('../dist/sw.js',import.meta.url));
copyFileSync(new URL('../public/index.html',import.meta.url),new URL('../dist/index.html',import.meta.url));
const result=spawnSync('mvn',['-q','-DskipTests','package'],{cwd:new URL('../airport-shuttle-ws/',import.meta.url),stdio:'inherit',env:{...process.env,MAVEN_OPTS:'-Xmx512m'}});
process.exit(result.status||0);
