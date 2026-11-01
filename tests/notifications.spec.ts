import { test,expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
test('service worker displays arrival alert and focuses its passenger trip',async()=>{
 const handlers:Record<string,(e:any)=>void>={};const notices:any[]=[];const navigations:string[]=[];let focused=false;
 const self={addEventListener:(type:string,handler:any)=>{handlers[type]=handler},registration:{showNotification:async(title:string,options:any)=>{notices.push({title,...options})}},clients:{matchAll:async()=>[{url:'http://localhost:8088/passenger/',navigate:async(url:string)=>navigations.push(url),focus:async()=>{focused=true}}],openWindow:async(url:string)=>navigations.push(url)}};
 runInNewContext(readFileSync('public/sw.js','utf8'),{self,URL});let completion:Promise<unknown>=Promise.resolve();
 handlers.push({data:{json:()=>({message:'Your shuttle is about 5 minutes away.',tripId:42})},waitUntil:(p:Promise<unknown>)=>{completion=p}});await completion;
 expect(notices[0]).toMatchObject({title:'Airport Shuttle',body:'Your shuttle is about 5 minutes away.',tag:'arrival-42'});
 handlers.notificationclick({notification:{data:notices[0].data,close:()=>{}},waitUntil:(p:Promise<unknown>)=>{completion=p}});await completion;expect(navigations).toEqual(['/passenger/?trip=42']);expect(focused).toBe(true);
});
