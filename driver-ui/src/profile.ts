import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IonButton, IonIcon } from '@ionic/angular/standalone';
import { Api, User } from './api';
import { I18n, TranslatePipe } from './i18n';

@Component({selector:'profile-view',standalone:true,imports:[CommonModule,RouterLink,IonIcon,TranslatePipe],template:`
 <section class="profile-page"><div class="profile-heading"><a routerLink="/" class="back-link" aria-label="{{'back'|tr}}"><ion-icon name="arrow-back-outline" /></a><h1>{{'profile'|tr}}</h1></div>
  <div class="profile-identity"><div class="profile-avatar" [class.has-image]="api.avatar()"><img *ngIf="api.avatar() as avatar" [src]="avatar" alt=""><span *ngIf="!api.avatar()">{{initials()}}</span></div><h2>{{api.user()?.name}}</h2><a routerLink="/profile/info">{{'editProfile'|tr}}</a></div>
  <section class="profile-stats"><div><strong>{{trips()}}</strong><span>{{'rides'|tr}}</span></div><div><strong>{{completed()}}</strong><span>{{'completed'|tr}}</span></div><div><strong>{{api.user()?.role | titlecase}}</strong><span>{{'accountType'|tr}}</span></div></section>
  <h2 class="profile-section-title">{{'accountSettings'|tr}}</h2><a routerLink="/profile/info" class="profile-setting"><ion-icon name="person-outline" /><span><strong>{{'yourInfo'|tr}}</strong><small>{{'updateYourInfo'|tr}}</small></span><ion-icon name="chevron-forward-outline" /></a>
 </section>`})
export class Profile {
 api=inject(Api);private router=inject(Router);private i18n=inject(I18n);
 trips(){return this.api.trips().length} completed(){return this.api.trips().filter(t=>t.status==='COMPLETED').length}
 initials(){return (this.api.user()?.name||'A').split(' ').map(n=>n[0]).join('').slice(0,2).toUpperCase()}
}

@Component({selector:'profile-info-view',standalone:true,imports:[CommonModule,FormsModule,RouterLink,IonButton,IonIcon,TranslatePipe],template:`
 <section class="profile-page info-page"><div class="profile-heading"><a routerLink="/profile" class="back-link" aria-label="{{'back'|tr}}"><ion-icon name="arrow-back-outline" /></a><h1>{{'yourInfo'|tr}}</h1></div><div class="profile-photo-editor"><div class="profile-avatar profile-avatar-small" [class.has-image]="api.avatar()"><img *ngIf="api.avatar() as avatar" [src]="avatar" alt=""><span *ngIf="!api.avatar()">{{initials()}}</span></div><label class="photo-upload"><ion-icon name="camera-outline" />{{'changePhoto'|tr}}<input type="file" accept="image/jpeg,image/png,image/webp" (change)="selectPhoto($event)"></label><button type="button" class="photo-remove" *ngIf="api.avatar()" (click)="removePhoto()">{{'removePhoto'|tr}}</button><small class="muted">{{'photoHint'|tr}}</small></div><h2 class="profile-section-title">{{'accountInfo'|tr}}</h2>
  <form class="profile-form" ngNativeValidate (ngSubmit)="save()"><div class="form-row"><label>{{'firstName'|tr}}<input name="firstName" [(ngModel)]="firstName" required maxlength="50" autocomplete="given-name"></label><label>{{'lastName'|tr}}<input name="lastName" [(ngModel)]="lastName" required maxlength="50" autocomplete="family-name"></label></div><label>{{'phone'|tr}}<input name="phone" type="tel" [(ngModel)]="phone" required maxlength="30" autocomplete="tel"></label><label>{{'email'|tr}}<input name="email" type="email" [(ngModel)]="email" required maxlength="190"></label><label>{{'newPassword'|tr}} <span class="optional">{{'optional'|tr}}</span><input name="password" type="password" [(ngModel)]="password" minlength="10" maxlength="72" autocomplete="new-password"></label><p class="muted">{{'passwordHint'|tr}}</p><div class="profile-form-actions"><a routerLink="/profile">{{'cancel'|tr}}</a><ion-button type="submit" [disabled]="api.busy()">{{api.busy() ? ('saving'|tr) : ('saveChanges'|tr)}}</ion-button></div></form>
 </section>`})
export class ProfileInfo {
 api=inject(Api);private i18n=inject(I18n);firstName='';lastName='';email='';phone='';password='';
 constructor(){const u=this.api.user();const name=(u?.name||'').trim().split(/\s+/);this.firstName=name.shift()||'';this.lastName=name.join(' ');this.email=u?.email||'';this.phone=u?.phone||''}
 initials(){return (this.api.user()?.name||'A').split(' ').map(n=>n[0]).join('').slice(0,2).toUpperCase()}
 async selectPhoto(event:Event){const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;if(!['image/jpeg','image/png','image/webp'].includes(file.type)){this.api.error.set(this.i18n.text('photoTypeError'));input.value='';return}if(file.size>5*1024*1024){this.api.error.set(this.i18n.text('photoSizeError'));input.value='';return}try{await this.api.upload('/auth/profile/photo',file);const reader=new FileReader();reader.onload=()=>{if(typeof reader.result==='string')this.api.saveAvatar(reader.result)};reader.readAsDataURL(file)}catch(e){this.api.error.set(e instanceof Error?e.message:'Could not save profile photo.')}finally{input.value=''}}
 async removePhoto(){try{await this.api.removeAvatar()}catch(e){this.api.error.set(e instanceof Error?e.message:'Could not remove profile photo.')}}
 async save(){const body={name:`${this.firstName.trim()} ${this.lastName.trim()}`.trim(),email:this.email.trim(),phone:this.phone.trim(),password:this.password};const ok=await this.api.act(async()=>{const user=await this.api.request<User>('/auth/profile','PUT',body);this.api.user.set(user);this.password=''},'Profile updated.');if(ok)history.replaceState({},'',location.pathname)}
}
