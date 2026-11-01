import { test,expect,Page,BrowserContext } from '@playwright/test';
const password='ShuttleLocal2026!';
async function login(page:Page,role:string,email:string){await page.goto('/'+role+'/');await page.getByLabel('Email',{exact:true}).fill(email+'@shuttle.local');await page.getByLabel('Password',{exact:true}).fill(password);await page.getByRole('button',{name:'Sign in',exact:true}).click();await expect(page.locator('h1')).toHaveText(role==='passenger'?'Your next ride.':role==='dispatcher'?'Trip queue':'Your schedule')}
async function api(context:BrowserContext,path:string,method='GET',data?:unknown){const r=await context.request.fetch('/api'+path,{method,headers:{'X-Shuttle-Request':'1'},data});expect(r.ok(),await r.text()).toBeTruthy();return r.json()}
async function noOverflow(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBeTruthy()}

test('passenger booking -> dispatch route -> driver GPS -> arrival -> receipt',async({browser},info)=>{
 const pctx=await browser.newContext(),dctx=await browser.newContext(),rctx=await browser.newContext({permissions:['geolocation'],geolocation:{latitude:40.751,longitude:-73.978,accuracy:10}});
 const passenger=await pctx.newPage(),dispatch=await dctx.newPage(),driver=await rctx.newPage();const errors:string[]=[];
 for(const page of [passenger,dispatch,driver])page.on('pageerror',e=>errors.push(e.message));
 await login(passenger,'passenger','passenger');
 await passenger.getByLabel('Flight number').fill('QA'+Date.now().toString().slice(-8));
 await passenger.getByLabel('Passengers',{exact:true}).fill('2');await passenger.getByLabel('Luggage',{exact:true}).fill('2');
 await passenger.getByLabel('Pickup notes').fill('Meet at the main entrance');
 await expect(passenger.locator('.map-caption')).toContainText('road estimate');
 await expect.poll(()=>passenger.locator('.leaflet-tile-loaded').count()).toBeGreaterThan(0);
 await passenger.screenshot({path:info.outputPath('passenger-booking-desktop.png'),fullPage:true});
 await passenger.getByRole('button',{name:'Request Ride',exact:true}).click();
 await expect(passenger.getByRole('status')).toContainText('Ride requested');
 const trips=await api(pctx,'/trips');const trip=trips.sort((a:any,b:any)=>b.id-a.id)[0];
 await login(dispatch,'dispatcher','dispatch');await dispatch.getByPlaceholder('Name, flight or trip ID').fill(trip.flight);
 await expect(dispatch.getByRole('button').filter({hasText:'Alex Morgan'})).toBeVisible();
 await dispatch.getByLabel('Assign driver',{exact:true}).selectOption({label:'Sam Rivera · 8 passengers · SH-101 | Mercedes Sprinter'});
 await dispatch.getByRole('button',{name:'Assign driver',exact:true}).click();await expect(dispatch.getByRole('status')).toContainText('Driver assigned');
 await dispatch.getByText('Modify route',{exact:true}).click();await dispatch.getByLabel('Extra stop',{exact:true}).selectOption({label:'Grand Marché de Bamako'});await dispatch.getByLabel('Instructions',{exact:true}).fill('Passenger pickup at main entrance');
 await dispatch.getByRole('button',{name:'Save route',exact:true}).click();await expect(dispatch.getByRole('status')).toContainText('Route updated');
 const routed=await api(dctx,'/trips/'+trip.id);expect(routed.via?.id).toBe(6);expect(routed.dispatchNotes).toBe('Passenger pickup at main entrance');
 await dispatch.screenshot({path:info.outputPath('dispatcher-desktop.png'),fullPage:true});
 await login(driver,'driver','driver');await driver.getByLabel('All dates').check();
 await driver.locator('.trip-row').filter({hasText:'AS-'+trip.id}).click();
 await driver.getByRole('button',{name:'Accept route',exact:true}).click();await expect(driver.getByRole('button',{name:'Start driving to pickup'})).toBeVisible();
 await expect(passenger.getByRole('heading',{name:'Driver confirmed',exact:true})).toBeVisible();
 await driver.getByRole('button',{name:'Start driving to pickup'}).click();await expect(driver.getByRole('button',{name:'Share live location',exact:true})).toBeVisible();
 await driver.getByLabel('ETA override (minutes)').fill('5');await driver.getByRole('button',{name:'Share live location',exact:true}).click();
 await expect(driver.getByText(/GPS updated/)).toBeVisible();await expect(passenger.locator('.arrival-notice')).toContainText('5 minutes away');
 await expect(passenger.getByText('5 min to pickup',{exact:true})).toBeVisible();await expect(passenger.locator('.pin-S')).toBeVisible();
 await driver.screenshot({path:info.outputPath('driver-desktop.png'),fullPage:true});
 await passenger.setViewportSize({width:390,height:844});await noOverflow(passenger);await passenger.screenshot({path:info.outputPath('passenger-live-mobile.png'),fullPage:true});
 await driver.setViewportSize({width:390,height:844});await noOverflow(driver);await driver.screenshot({path:info.outputPath('driver-mobile.png'),fullPage:true});
 await dispatch.setViewportSize({width:390,height:844});await noOverflow(dispatch);await dispatch.screenshot({path:info.outputPath('dispatcher-mobile.png'),fullPage:true});
 for(const label of ['Mark arriving','Start boarding','Start journey','Complete trip']){await driver.getByRole('button',{name:label,exact:true}).click();await expect(driver.getByRole('button',{name:label,exact:true})).toHaveCount(0)}
 await passenger.locator('ion-segment-button[value="history"]').click();
 await passenger.locator('.trip-row').filter({hasText:'AS-'+trip.id.toString().padStart(4,'0')}).click();
 const download=passenger.waitForEvent('download');await passenger.getByRole('link',{name:'Download receipt'}).click();expect((await download).suggestedFilename()).toBe('shuttle-'+trip.id+'-receipt.txt');
 await passenger.reload();await passenger.locator('ion-segment-button[value="history"]').click();await expect(passenger.locator('.trip-row').filter({hasText:'AS-'+trip.id.toString().padStart(4,'0')})).toBeVisible();
 expect(errors).toEqual([]);await pctx.close();await dctx.close();await rctx.close();
});

test('registration, invalid form, cancellation, session logout and offline recovery',async({page,context})=>{
 await page.goto('/passenger/');await page.getByRole('button',{name:'New passenger? Create an account'}).click();await page.getByLabel('Full name').fill('Browser Test Passenger');await page.getByLabel('Email',{exact:true}).fill('browser-'+Date.now()+'@example.com');await page.getByLabel('Password',{exact:true}).fill('BrowserPassword123!');await page.getByRole('button',{name:'Create account',exact:true}).click();await expect(page.getByRole('heading',{name:'Your next ride.'})).toBeVisible();
 await page.getByRole('button',{name:'Request Ride',exact:true}).click();expect(await page.getByLabel('Flight number').evaluate((e:HTMLInputElement)=>e.validity.valueMissing)).toBeTruthy();expect(await api(context,'/trips')).toHaveLength(0);
 await page.getByLabel('Pickup spot').selectOption({label:'Aéroport International Président Modibo Keïta-Sénou (BKO)'});await expect(page.getByLabel('Destination',{exact:true})).toHaveValue(/4/);await page.getByLabel('Flight number').fill('QA 101');await page.getByRole('button',{name:'Request Ride',exact:true}).click();await expect(page.getByRole('status')).toContainText('Ride requested');
 page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Cancel ride',exact:true}).click();await expect(page.getByRole('status')).toContainText('Ride cancelled');await page.locator('ion-segment-button[value="history"]').click();await expect(page.locator('.trip-row')).toContainText('Cancelled');
 await context.setOffline(true);await expect(page.getByText('Connection lost. Trip information may be out of date.')).toBeVisible({timeout:25000});await context.setOffline(false);await expect(page.getByText('Connection lost. Trip information may be out of date.')).toHaveCount(0);
 await page.getByRole('button',{name:'Sign out',exact:true}).click();await expect(page.getByRole('heading',{name:'Sign in',exact:true})).toBeVisible();expect((await context.request.get('/api/trips')).status()).toBe(401);
});

test('wrong-role login and unauthenticated protected data',async({page,request})=>{
 expect((await request.get('/api/trips')).status()).toBe(401);await page.goto('/dispatcher/');await page.getByLabel('Email',{exact:true}).fill('passenger@shuttle.local');await page.getByLabel('Password',{exact:true}).fill(password);await page.getByRole('button',{name:'Sign in',exact:true}).click();await expect(page.getByRole('alert')).toContainText('passenger workspace');await expect(page.getByRole('heading',{name:'Dispatch control'})).toHaveCount(0);
});

test('two dispatchers cannot overwrite the same assignment',async({browser})=>{
 const p=await browser.newContext(),a=await browser.newContext(),b=await browser.newContext();
 for(const [ctx,email] of [[p,'passenger'],[a,'dispatch'],[b,'dispatch']] as const)await api(ctx,'/auth/login','POST',{email:email+'@shuttle.local',password});
 const trip=await api(p,'/trips','POST',{pickupId:4,destinationId:1,flight:'CONCURRENT',pickupAt:new Date(Date.now()+5*86400000).toISOString(),passengers:1,bags:1,notes:'',requestKey:crypto.randomUUID()});const drivers=await api(a,'/drivers');
 const responses=await Promise.all([a.request.put('/api/trips/'+trip.id+'/assignment',{headers:{'X-Shuttle-Request':'1'},data:{driverId:drivers[0].id,version:0}}),b.request.put('/api/trips/'+trip.id+'/assignment',{headers:{'X-Shuttle-Request':'1'},data:{driverId:drivers[1].id,version:0}})]);
 expect(responses.map(r=>r.status()).sort()).toEqual([200,409]);const updated=await api(a,'/trips/'+trip.id);await api(a,'/trips/'+trip.id+'/status','PUT',{status:'CANCELLED',version:updated.version});await p.close();await a.close();await b.close();
});

test('all three workspaces keep separate logins in one browser',async({context})=>{
 const passenger=await context.newPage(),dispatch=await context.newPage(),driver=await context.newPage();
 await login(passenger,'passenger','passenger');await login(dispatch,'dispatcher','dispatch');await login(driver,'driver','driver');
 await passenger.reload();await expect(passenger.getByRole('heading',{name:'Your next ride.'})).toBeVisible();
 await dispatch.reload();await expect(dispatch.locator('h1')).toHaveText('Trip queue');
 await driver.getByRole('button',{name:'Sign out',exact:true}).click();await expect(driver.getByRole('heading',{name:'Sign in',exact:true})).toBeVisible();
 await passenger.reload();await expect(passenger.getByRole('heading',{name:'Your next ride.'})).toBeVisible();
 await dispatch.reload();await expect(dispatch.locator('h1')).toHaveText('Trip queue');
 const concurrentReads=await Promise.all(Array.from({length:16},()=>context.request.get('/api/trips',{headers:{'X-Shuttle-Role':'DISPATCHER'},timeout:10000})));
 expect(concurrentReads.every(response=>response.status()===200)).toBe(true);
});

test('passenger can switch to French and see Francs CFA fare',async({page})=>{
 await login(page,'passenger','passenger');
 await page.getByLabel('Language').selectOption('fr');
 await expect(page.locator('h1')).toHaveText('Votre prochain trajet.');
 await expect(page.getByRole('button',{name:'Demander un trajet'})).toBeVisible();
 await expect(page.locator('.fare strong')).toHaveText('22\u202f500 Francs CFA');
 await page.getByLabel('Language').selectOption('en');
 await expect(page.locator('.fare strong')).toHaveText('$45.00');
});
