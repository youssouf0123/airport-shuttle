import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { provideRouter,RouterOutlet,Routes } from '@angular/router';
import { Shell } from './shell';
import { ROLE } from './api';
import { Dispatcher } from './dispatcher';
import { FleetManagement } from './fleet-management';
import { Profile, ProfileInfo } from './profile';
import { DispatcherHistory } from './history';
const routes:Routes=[{path:'drivers/assign',component:FleetManagement},{path:'drivers',component:FleetManagement},{path:'history',component:DispatcherHistory},{path:'profile/info',component:ProfileInfo},{path:'profile',component:Profile},{path:'',pathMatch:'full',component:Dispatcher},{path:'**',redirectTo:''}];
@Component({selector:'app-root',standalone:true,imports:[Shell,RouterOutlet],template:'<shuttle-shell><ng-template><router-outlet /></ng-template></shuttle-shell>'}) class App {}
bootstrapApplication(App,{providers:[provideIonicAngular({mode:'md'}),provideRouter(routes),{provide:ROLE,useValue:'DISPATCHER'}]}).catch(console.error);
