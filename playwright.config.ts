import { defineConfig } from '@playwright/test';
export default defineConfig({
 testDir:'./tests',fullyParallel:false,workers:1,timeout:90000,expect:{timeout:12000},
 reporter:[['list'],['html',{open:'never'}]],
 use:{baseURL:'http://localhost:8090',viewport:{width:1440,height:1000},actionTimeout:15000,trace:'retain-on-failure',screenshot:'only-on-failure'},
 webServer:{command:'node scripts/start.mjs',url:'http://localhost:8090/api/health',timeout:60000,reuseExistingServer:false,env:{PORT:'8090',DB_URL:'jdbc:mysql://127.0.0.1:3306/airport_shuttle_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'}},
});
