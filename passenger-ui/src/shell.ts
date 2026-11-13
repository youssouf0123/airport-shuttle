import { Component, ContentChild, TemplateRef, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonApp, IonButton, IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { airplaneOutline,logOutOutline,arrowForwardOutline,locationOutline,timeOutline,peopleOutline,bagHandleOutline,navigateOutline,checkmarkOutline,closeOutline,refreshOutline,downloadOutline,notificationsOutline,carOutline,calendarOutline,swapHorizontalOutline,menuOutline,homeOutline,settingsOutline,helpCircleOutline,moonOutline,sunnyOutline,arrowBackOutline,personOutline,chevronForwardOutline,cameraOutline,createOutline } from 'ionicons/icons';
import { Api,User } from './api';
import { I18n, TranslatePipe } from './i18n';
import { RouterLink } from '@angular/router';
addIcons({airplaneOutline,logOutOutline,arrowForwardOutline,locationOutline,timeOutline,peopleOutline,bagHandleOutline,navigateOutline,checkmarkOutline,closeOutline,refreshOutline,downloadOutline,notificationsOutline,carOutline,calendarOutline,swapHorizontalOutline,menuOutline,homeOutline,settingsOutline,helpCircleOutline,moonOutline,sunnyOutline,arrowBackOutline,personOutline,chevronForwardOutline,cameraOutline,createOutline});
@Component({selector:'shuttle-shell',standalone:true,imports:[CommonModule,FormsModule,RouterLink,IonApp,IonButton,IonIcon,TranslatePipe],template:`
 <ion-app>
  <header class="topbar"><button class="menu-button icon-button" aria-label="Open navigation" [attr.aria-expanded]="menuOpen()" (click)="menuOpen.set(!menuOpen())"><ion-icon name="menu-outline" /></button><a class="brand" [href]="'/' + rolePath + '/'"><span class="brand-symbol"><ion-icon name="airplane-outline" /></span><span>Airport Shuttle<small>{{api.role === 'PASSENGER' ? ('passenger'|tr) : api.role === 'DRIVER' ? ('driver'|tr) : ('dispatch'|tr)}}</small></span></a>
<div class="account"><button class="icon-button" [title]="darkMode() ? ('lightMode'|tr) : ('darkMode'|tr)" [attr.aria-label]="darkMode() ? ('lightMode'|tr) : ('darkMode'|tr)" (click)="toggleTheme()"><ion-icon [name]="darkMode() ? 'sunny-outline' : 'moon-outline'" /></button><label class="language-picker"><span class="sr-only">Language</span><select [ngModel]="i18n.language()" (ngModelChange)="changeLanguage($event)" aria-label="Language"><option value="en">English</option><option value="fr">Français</option></select></label><ng-container *ngIf="api.user() as user"><span class="connection" [class.disconnected]="!api.online()">{{api.online() ? ('connected'|tr) : ('reconnecting'|tr)}}</span><span class="user-name">{{user.name}}</span><button class="icon-button" title="{{'signOut'|tr}}" aria-label="{{'signOut'|tr}}" (click)="logout()"><ion-icon name="log-out-outline" /></button></ng-container></div>
  </header>
  <div class="nav-scrim" *ngIf="menuOpen()" (click)="menuOpen.set(false)"></div><aside class="side-nav" [class.open]="menuOpen()" aria-label="Application navigation"><div class="side-nav-header"><strong>Airport Shuttle</strong><button class="icon-button" aria-label="Close navigation" (click)="menuOpen.set(false)"><ion-icon name="close-outline" /></button></div><a routerLink="/profile" class="profile-menu" (click)="menuOpen.set(false)" *ngIf="api.user() as user"><div class="profile-menu-avatar" [class.has-image]="api.avatar()"><img *ngIf="api.avatar() as avatar" [src]="avatar" alt=""><span *ngIf="!api.avatar()">{{initials(user.name)}}</span></div><strong>{{user.name}}</strong><span>{{'viewProfile'|tr}}</span></a><nav><a class="side-nav-link active" routerLink="/" (click)="menuOpen.set(false)"><ion-icon name="home-outline" />{{'dashboard'|tr}}</a><a class="side-nav-link" routerLink="/drivers" (click)="menuOpen.set(false)" *ngIf="api.role==='DISPATCHER'"><ion-icon name="people-outline" />{{'manageDrivers'|tr}}</a><button class="side-nav-link" type="button"><ion-icon name="settings-outline" />{{'settings'|tr}}</button><button class="side-nav-link" type="button"><ion-icon name="help-circle-outline" />{{'help'|tr}}</button></nav></aside>
  <div class="message error" role="alert" *ngIf="api.error()">{{api.error()}}<button aria-label="Dismiss error" (click)="api.error.set('')">&times;</button></div>
  <div class="message success" role="status" *ngIf="api.success()">{{api.success()}}<button aria-label="Dismiss confirmation" (click)="api.success.set('')">&times;</button></div>
  <div class="message error" *ngIf="api.user() && !api.online()">{{'connectionLost'|tr}}<button (click)="api.refresh()">{{'retry'|tr}}</button></div>
  <main class="page" *ngIf="!checking(); else loading">
   <ng-container *ngIf="api.user(); else signin"><ng-container *ngIf="api.loaded(); else loading"><ng-container *ngTemplateOutlet="content || null" /></ng-container></ng-container>
   <ng-template #signin>
    <div class="auth-layout"><div class="auth-brand"><div class="eyebrow">{{'jfkConnections'|tr}}</div><h1>{{api.role === 'PASSENGER' ? ('yourNextRide'|tr) : api.role === 'DRIVER' ? ('readyRoad'|tr) : ('everyTrip'|tr)}}</h1><p>{{api.role === 'PASSENGER' ? ('cityTerminal'|tr) : api.role === 'DRIVER' ? ('scheduleTag'|tr) : ('operations'|tr)}}</p><div class="auth-line"><ion-icon name="location-outline" /> {{'newYork'|tr}} <span></span><ion-icon name="airplane-outline" /> {{'jfk'|tr}}</div></div>
    <form class="auth-form" ngNativeValidate (ngSubmit)="authenticate()"><h2>{{register ? ('createYourAccount'|tr) : ('signIn'|tr)}}</h2>
     <ng-container *ngIf="register"><div class="form-row"><label>{{'firstName'|tr}}<input name="firstName" [(ngModel)]="firstName" required maxlength="50" autocomplete="given-name"></label><label>{{'lastName'|tr}}<input name="lastName" [(ngModel)]="lastName" required maxlength="50" autocomplete="family-name"></label></div><label>{{'phone'|tr}}<input name="phone" type="tel" [(ngModel)]="phone" required maxlength="30" autocomplete="tel"></label></ng-container>
     <label>{{'email'|tr}}<input name="email" type="email" [(ngModel)]="email" required maxlength="190" autocomplete="username"></label>
     <label>{{'password'|tr}}<input name="password" type="password" [(ngModel)]="password" required [minlength]="register ? 10 : 1" maxlength="72" [autocomplete]="register ? 'new-password' : 'current-password'"></label>
     <ion-button type="submit" expand="block" [disabled]="api.busy()">{{api.busy() ? 'Please wait...' : register ? ('createAccount'|tr) : ('signIn'|tr)}}<ion-icon slot="end" name="arrow-forward-outline" /></ion-button>
     <button type="button" class="text-button" *ngIf="api.role==='PASSENGER'" (click)="register=!register;api.error.set('')">{{register ? ('alreadyAccount'|tr) : ('newPassenger'|tr)}}</button>
    </form></div>
   </ng-template>
  </main>
  <ng-template #loading><div class="empty" role="status">{{'loading'|tr}}</div></ng-template>
 </ion-app>`})
export class Shell implements OnInit,OnDestroy {
 @ContentChild(TemplateRef) content?:TemplateRef<unknown>;
 api=inject(Api);i18n=inject(I18n);checking=signal(true);menuOpen=signal(false);themeKey=`airport-shuttle-theme:${this.api.role.toLowerCase()}`;darkMode=signal(localStorage.getItem(this.themeKey)==='dark');email='';password='';firstName='';lastName='';phone='';register=false;timer?:ReturnType<typeof setInterval>;
 get rolePath(){return this.api.role.toLowerCase()}
 initials(name:string){return name.split(' ').map(n=>n[0]).join('').slice(0,2).toUpperCase()}
 changeLanguage(language:'en'|'fr'){this.i18n.setLanguage(language)}
 toggleTheme(){const dark=!this.darkMode();this.darkMode.set(dark);localStorage.setItem(this.themeKey,dark?'dark':'light');document.documentElement.classList.toggle('dark-mode',dark)}
async ngOnInit(){document.documentElement.classList.toggle('dark-mode',this.darkMode());try{const u=await this.api.request<User>('/auth/me');if(u.role===this.api.role){this.api.user.set(u);await this.api.load()}else this.api.error.set('Sign in with a '+this.rolePath+' account to open this workspace.')}catch{}finally{this.checking.set(false)}this.timer=setInterval(()=>this.api.refresh(),3000)}
 async authenticate(){await this.api.act(async()=>{const body=this.register?{email:this.email,password:this.password,name:`${this.firstName.trim()} ${this.lastName.trim()}`.trim(),phone:this.phone.trim()}:{email:this.email,password:this.password};const u=await this.api.request<User>(this.register?'/auth/register':'/auth/login','POST',body);if(u.role!==this.api.role){throw new Error('This account belongs to the '+u.role.toLowerCase()+' workspace. Use the matching link below.')}this.api.user.set(u);this.password='';this.register=false;await this.api.load()},'')}
 async logout(){await this.api.act(async()=>{if(this.api.role==='PASSENGER'&&'serviceWorker' in navigator){const reg=await navigator.serviceWorker.getRegistration('/');const sub=await reg?.pushManager.getSubscription();if(sub){await this.api.request('/push/unsubscribe','POST',{endpoint:sub.endpoint});await sub.unsubscribe()}}await this.api.request('/auth/logout','POST');this.api.user.set(null);this.api.clearData()},'')}
 ngOnDestroy(){clearInterval(this.timer)}
}
