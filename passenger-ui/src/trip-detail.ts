import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';
import { Api, Trip,statusLabel } from './api';
import { TranslatePipe, MoneyPipe, StatusPipe } from './i18n';
@Component({selector:'trip-detail',standalone:true,imports:[CommonModule,IonIcon,TranslatePipe,MoneyPipe,StatusPipe],styles:[`.wait-clock{display:flex;justify-content:space-between;gap:12px;padding:12px 14px;margin:12px 0;border-radius:10px;background:var(--soft,#eef5f3);color:var(--text,#20312e)}.wait-clock strong{font-variant-numeric:tabular-nums}.driver-contact{display:flex;flex-direction:column;gap:10px}.call-link{display:inline-flex;align-items:center;gap:8px;width:max-content;padding:10px 14px;border-radius:9px;background:var(--accent,#087c66);color:#fff;text-decoration:none;font-weight:700}.driver-face{order:3;margin-left:auto;width:58px;height:58px;border-radius:50%;overflow:hidden;flex:0 0 58px;background:#e7efed;color:#70837d;display:grid;place-items:center}.driver-face img{width:100%;height:100%;object-fit:cover}.driver-face ion-icon{font-size:54px}.driver-info{align-items:center}.driver-info>ion-icon{order:1}.driver-contact{order:2}`],template:`
 <ng-container *ngIf="trip as t"><div class="detail-title"><span class="reference">AS-{{t.id.toString().padStart(4,'0')}}</span><span class="status" [attr.data-status]="t.status">{{t.status|status}}</span></div>
 <div class="route-summary"><div><span class="route-dot"></span><small>{{'pickup'|tr}}</small><strong>{{t.pickup.name}}</strong><span>{{t.pickup.detail}}</span></div><div *ngIf="t.via"><span class="route-dot via"></span><small>{{'extraStop'|tr}}</small><strong>{{t.via.name}}</strong></div><div><span class="route-dot destination"></span><small>{{'destination'|tr}}</small><strong>{{t.destination.name}}</strong><span>{{t.destination.detail}}</span></div></div>
 <div class="facts"><div><ion-icon name="calendar-outline" /><span>{{t.pickupAt | date:'MMM d, h:mm a'}}</span></div><div><ion-icon name="airplane-outline" /><span>{{t.flight}}</span></div><div><ion-icon name="people-outline" /><span>{{t.passengers}} {{'passengers'|tr|lowercase}}</span></div><div><ion-icon name="bag-handle-outline" /><span>{{t.bags}} {{'luggage'|tr|lowercase}}</span></div></div>
 <div class="wait-clock" *ngIf="t.status==='ARRIVING' && t.arrivedAt"><span>{{'waitingAtPickup'|tr}}</span><strong>{{waitDuration(t)}}</strong></div>
 <div class="driver-info" *ngIf="t.driverName"><div class="driver-face" *ngIf="allowCall && accepted(t)"><img *ngIf="driverPhotoUrl; else defaultDriverFace" [src]="driverPhotoUrl" alt="{{t.driverName}}"><ng-template #defaultDriverFace><ion-icon name="person-circle-outline" /></ng-template></div><ion-icon name="car-outline" /><div class="driver-contact"><strong>{{t.driverName}}</strong><span>{{t.vehicle}}</span><a class="call-link" *ngIf="allowCall && accepted(t) && t.driverPhone" [href]="'tel:'+t.driverPhone"><ion-icon name="call-outline" />{{'callDriver'|tr}}</a></div></div>
 <p class="note" *ngIf="t.notes"><strong>{{'passengerNote'|tr}}</strong>{{t.notes}}</p><p class="note" *ngIf="t.dispatchNotes"><strong>{{'dispatchNote'|tr}}</strong>{{t.dispatchNotes}}</p>
 <div class="fare"><span>{{'estimatedFare'|tr}}</span><strong>{{t.fareCents | money:t.fareXof}}</strong></div>
 </ng-container>`})
 export class TripDetail implements OnChanges,OnInit,OnDestroy {
 private api=inject(Api);driverPhotoUrl:string|null=null;private loadedPhotoKey='';
 private waitTimer?:ReturnType<typeof setInterval>;private now=signal(Date.now());
 @Input({required:true}) trip!:Trip;@Input() allowCall=false;label=statusLabel;
 accepted(t:Trip){return !!t.driverId&&['ACCEPTED','EN_ROUTE','ARRIVING','BOARDING','IN_TRANSIT'].includes(t.status)}
 waitDuration(t:Trip){const seconds=Math.max(0,t.waitSeconds??(t.arrivedAt?Math.floor((this.now()-Date.parse(t.arrivedAt))/1000):0));return `${Math.floor(seconds/60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}`}
 ngOnInit(){this.waitTimer=setInterval(()=>this.now.set(Date.now()),1000)}
 ngOnChanges(_changes:SimpleChanges){const t=this.trip;const key=this.allowCall&&this.accepted(t)?`${t.id}:${t.driverId}`:'';if(key===this.loadedPhotoKey)return;this.clearPhoto();this.loadedPhotoKey=key;if(key)this.api.file(`/trips/${t.id}/driver-photo`).then(blob=>{if(this.loadedPhotoKey===key)this.driverPhotoUrl=URL.createObjectURL(blob)}).catch(()=>{})}
 private clearPhoto(){if(this.driverPhotoUrl?.startsWith('blob:'))URL.revokeObjectURL(this.driverPhotoUrl);this.driverPhotoUrl=null}
 ngOnDestroy(){this.clearPhoto();if(this.waitTimer)clearInterval(this.waitTimer)}
}
