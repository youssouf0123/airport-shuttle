import { Component, inject, computed, signal, OnDestroy, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonButton, IonIcon } from '@ionic/angular/standalone';
import { Api,Trip,isActive,statusLabel } from '../../shared/api';
import { I18n, TranslatePipe, StatusPipe } from '../../shared/i18n';
import { TripMap } from '../../shared/map';
import { TripDetail } from '../../shared/trip-detail';
@Component({selector:'driver-view',standalone:true,imports:[CommonModule,FormsModule,IonButton,IonIcon,TripMap,TripDetail,TranslatePipe,StatusPipe],template:`
 <div class="page-heading"><div><div class="eyebrow">{{'driver'|tr|uppercase}}</div><h1>{{'schedule'|tr}}</h1></div><span class="vehicle-label"><ion-icon name="car-outline" />{{api.user()?.vehicle}}</span></div>
 <div class="driver-toolbar"><label>{{'scheduleDate'|tr}}<input type="date" [ngModel]="date()" (ngModelChange)="date.set($event)"></label><label class="checkbox-label"><input type="checkbox" [ngModel]="allDates()" (ngModelChange)="allDates.set($event)">{{'allDates'|tr}}</label><span>{{visible().length}} {{'trips'|tr}} · {{completed()}} {{'completed'|tr}}</span></div>
 <div class="trip-workspace" *ngIf="visible().length; else noTrips"><aside class="trip-list"><button class="trip-row" *ngFor="let t of visible()" [class.selected]="selected()?.id===t.id" (click)="selectedId.set(t.id)"><div><strong>{{t.pickupAt | date:'h:mm a'}}</strong><span class="status" [attr.data-status]="t.status">{{t.status|status}}</span></div><strong>{{t.pickup.name}}</strong><span>{{t.passengerName}} · {{t.passengers}} {{'passengers'|tr|lowercase}}</span><small>{{t.pickupAt | date:'EEE, MMM d'}} · AS-{{t.id}}</small></button></aside>
 <section class="selected-trip" *ngIf="selected() as t"><div class="tracking-heading"><div><span class="eyebrow">{{t.passengerName}}</span><h2>{{t.status|status}}</h2></div><a *ngIf="active(t)" class="download-link" [href]="navigation(t)" target="_blank" rel="noopener"><ion-icon name="navigate-outline" />{{'navigate'|tr}}</a></div><trip-map [trip]="t" />
  <div class="driver-actions" *ngIf="active(t)"><div *ngIf="t.status==='ASSIGNED'" class="actions"><ion-button (click)="status(t,'ACCEPTED')" [disabled]="api.busy()"><ion-icon slot="start" name="checkmark-outline" />Accept route</ion-button><ion-button fill="outline" color="danger" (click)="decline(t)" [disabled]="api.busy()">Decline</ion-button></div>
   <ion-button *ngIf="next(t) && t.status!=='ASSIGNED'" expand="block" (click)="status(t,next(t)!.status)" [disabled]="api.busy()">{{next(t)!.label}}<ion-icon slot="end" name="arrow-forward-outline" /></ion-button>
   <div class="location-controls" *ngIf="canShare(t)"><ion-button fill="outline" (click)="sharingId()===t.id ? stopSharing() : share(t)" [disabled]="sharingId()!==null && sharingId()!==t.id"><ion-icon slot="start" name="location-outline" />{{sharingId()===t.id ? 'Stop location sharing' : 'Share live location'}}</ion-button><span class="muted">{{sharingId()===t.id ? gpsMessage() : 'Location sharing is off'}}</span><label>ETA override (minutes)<input type="number" [(ngModel)]="etaOverride" min="0" max="240" placeholder="Automatic"></label><ion-button fill="clear" *ngIf="sharingId()===t.id" (click)="resend()">Update ETA</ion-button></div>
  </div><trip-detail [trip]="t" /></section></div>
 <ng-template #noTrips><div class="empty"><ion-icon name="calendar-outline" /><h2>No routes scheduled</h2><p>{{allDates() ? 'Your assignments will appear here.' : 'No trips for this date.'}}</p><ion-button *ngIf="!allDates()" fill="outline" (click)="allDates.set(true)">View all dates</ion-button></div></ng-template>`})
 export class Driver implements OnDestroy {
 api=inject(Api);i18n=inject(I18n);label=statusLabel;active=isActive;date=signal(new Date().toLocaleDateString('en-CA'));allDates=signal(false);selectedId=signal<number|null>(null);sharingId=signal<number|null>(null);gpsMessage=signal(this.i18n.text('waitingGps'));watchId:number|null=null;etaOverride:number|null=null;lastPosition:GeolocationPosition|null=null;lastSent=0;sending=false;
 visible=computed(()=>this.api.trips().filter(t=>this.allDates()||new Date(t.pickupAt).toLocaleDateString('en-CA')===this.date()));selected=computed(()=>this.visible().find(t=>t.id===this.selectedId())||this.visible().find(isActive)||this.visible()[0]||null);completed=computed(()=>this.visible().filter(t=>t.status==='COMPLETED').length);
 constructor(){effect(()=>{const id=this.sharingId();if(id&&!this.api.trips().some(t=>t.id===id&&this.canShare(t)))this.stopSharing()})}
 next(t:Trip){const n=({ASSIGNED:{status:'ACCEPTED',key:'acceptRoute'},ACCEPTED:{status:'EN_ROUTE',key:'startDriving'},EN_ROUTE:{status:'ARRIVING',key:'markArriving'},ARRIVING:{status:'BOARDING',key:'startBoarding'},BOARDING:{status:'IN_TRANSIT',key:'startJourney'},IN_TRANSIT:{status:'COMPLETED',key:'completeTrip'}} as Record<string,{status:string;key:string}>)[t.status];return n&&{status:n.status,label:this.i18n.text(n.key)}}
 status(t:Trip,status:string){this.api.act(()=>this.api.request('/trips/'+t.id+'/status','PUT',{status,version:t.version}),'Trip updated.')}
 decline(t:Trip){if(confirm('Decline this assignment and return it to dispatch?'))this.status(t,'REQUESTED')}
 navigation(t:Trip){const destination=t.status==='IN_TRANSIT'?t.destination:t.pickup;const via=t.status==='IN_TRANSIT'&&t.via?`&waypoints=${t.via.lat},${t.via.lng}`:'';return `https://www.google.com/maps/dir/?api=1&destination=${destination.lat},${destination.lng}&travelmode=driving${via}`}
 canShare(t:Trip){return ['EN_ROUTE','ARRIVING','BOARDING','IN_TRANSIT'].includes(t.status)}
 share(t:Trip){if(!navigator.geolocation){this.api.error.set('Geolocation is not available in this browser.');return}this.stopSharing();this.sharingId.set(t.id);this.lastSent=0;this.gpsMessage.set('Waiting for GPS...');this.watchId=navigator.geolocation.watchPosition(p=>{this.lastPosition=p;if(Date.now()-this.lastSent>8000)this.send(p)},e=>{this.api.error.set(e.code===1?'Location access was denied. Enable it in your browser settings.':'GPS is unavailable. Check location settings and try again.');this.stopSharing()},{enableHighAccuracy:true,maximumAge:5000,timeout:15000})}
 async send(p:GeolocationPosition){const t=this.api.trips().find(t=>t.id===this.sharingId());if(!t||this.sending)return;this.sending=true;this.lastSent=Date.now();try{
  let eta=this.etaOverride;
  if(eta!==null&&(!Number.isInteger(eta)||eta<0||eta>240))throw new Error('ETA must be a whole number between 0 and 240.');
  if(eta===null){const dest=t.status==='IN_TRANSIT'?t.destination:t.pickup;try{const res=await fetch(`https://router.project-osrm.org/route/v1/driving/${p.coords.longitude},${p.coords.latitude};${dest.lng},${dest.lat}?overview=false`,{signal:AbortSignal.timeout(8000)});const route=await res.json();if(route.routes?.[0])eta=Math.min(240,Math.ceil(route.routes[0].duration/60))}catch{}}
  if(this.sharingId()!==t.id)return;await this.api.request('/trips/'+t.id+'/location','PUT',{lat:p.coords.latitude,lng:p.coords.longitude,etaMinutes:eta});this.gpsMessage.set('GPS updated '+new Date().toLocaleTimeString());await this.api.refresh();
 }catch(e){this.api.error.set(e instanceof Error?e.message:'Location update failed.')}finally{this.sending=false}}
 resend(){if(this.lastPosition)this.send(this.lastPosition)}
 stopSharing(){if(this.watchId!==null)navigator.geolocation.clearWatch(this.watchId);this.watchId=null;this.sharingId.set(null)}
 ngOnDestroy(){this.stopSharing()}
}
