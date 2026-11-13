import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';
import { Api, Trip,statusLabel } from './api';
import { TranslatePipe, StatusPipe } from './i18n';
@Component({selector:'trip-detail',standalone:true,imports:[CommonModule,IonIcon,TranslatePipe,StatusPipe],styles:[`.wait-clock{display:flex;justify-content:space-between;gap:12px;padding:12px 14px;margin:12px 0;border-radius:10px;background:var(--soft,#eef5f3);color:var(--text,#20312e)}.wait-clock strong{font-variant-numeric:tabular-nums}.passenger-photo-row{display:flex;align-items:center;gap:13px;padding:12px 0 18px}.passenger-face{order:2;margin-left:auto;width:58px;height:58px;border-radius:50%;overflow:hidden;flex:0 0 58px;background:#e7efed;color:#70837d;display:grid;place-items:center}.passenger-face img{width:100%;height:100%;object-fit:cover}.passenger-face ion-icon{font-size:54px}.passenger-photo-row small,.passenger-photo-row strong{display:block}.passenger-photo-row small{color:var(--muted);margin-bottom:4px}.passenger-photo-row .call-link{display:inline-flex;align-items:center;gap:7px;margin-top:9px;padding:8px 12px;border-radius:8px;background:var(--accent,#087c66);color:#fff;text-decoration:none;font-weight:700}`],template:`
 <ng-container *ngIf="trip as t"><div class="detail-title"><span class="reference">AS-{{t.id.toString().padStart(4,'0')}}</span><span class="status" [attr.data-status]="t.status">{{t.status|status}}</span></div>
 <div class="route-summary"><div><span class="route-dot"></span><small>{{'pickup'|tr}}</small><strong>{{t.pickup.name}}</strong><span>{{t.pickup.detail}}</span></div><div *ngIf="t.via"><span class="route-dot via"></span><small>{{'extraStop'|tr}}</small><strong>{{t.via.name}}</strong></div><div><span class="route-dot destination"></span><small>{{'destination'|tr}}</small><strong>{{t.destination.name}}</strong><span>{{t.destination.detail}}</span></div></div>
 <div class="facts"><div><ion-icon name="calendar-outline" /><span>{{t.pickupAt | date:'MMM d, h:mm a'}}</span></div><div><ion-icon name="airplane-outline" /><span>{{t.flight}}</span></div><div><ion-icon name="people-outline" /><span>{{t.passengers}} {{'passengers'|tr|lowercase}}</span></div><div><ion-icon name="bag-handle-outline" /><span>{{t.bags}} {{'luggage'|tr|lowercase}}</span></div></div>
 <div class="wait-clock" *ngIf="t.status==='ARRIVING' && t.arrivedAt"><span>{{'waitingAtPickup'|tr}}</span><strong>{{waitDuration(t)}}</strong></div>
 <div class="passenger-photo-row" *ngIf="accepted(t)"><div class="passenger-face"><img *ngIf="passengerPhotoUrl; else defaultPassengerFace" [src]="passengerPhotoUrl" alt="{{t.passengerName}}"><ng-template #defaultPassengerFace><ion-icon name="person-circle-outline" /></ng-template></div><div><small>{{'passenger'|tr}}</small><strong>{{t.passengerName}}</strong><a class="call-link" *ngIf="t.passengerPhone" [href]="'tel:'+t.passengerPhone"><ion-icon name="call-outline" />{{'callPassenger'|tr}}</a></div></div>
 <p class="note" *ngIf="t.notes"><strong>{{'passengerNote'|tr}}</strong>{{t.notes}}</p><p class="note" *ngIf="t.dispatchNotes"><strong>{{'dispatchNote'|tr}}</strong>{{t.dispatchNotes}}</p>
 </ng-container>`})
 export class TripDetail implements OnChanges,OnInit,OnDestroy {
 private api=inject(Api);passengerPhotoUrl:string|null=null;private loadedPhotoKey='';
 private waitTimer?:ReturnType<typeof setInterval>;private now=signal(Date.now());
 @Input({required:true}) trip!:Trip;label=statusLabel;
 accepted(t:Trip){return !!t.driverId&&['ACCEPTED','EN_ROUTE','ARRIVING','BOARDING','IN_TRANSIT'].includes(t.status)}
 waitDuration(t:Trip){const seconds=Math.max(0,t.waitSeconds??(t.arrivedAt?Math.floor((this.now()-Date.parse(t.arrivedAt))/1000):0));return `${Math.floor(seconds/60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}`}
 ngOnInit(){this.waitTimer=setInterval(()=>this.now.set(Date.now()),1000)}
 ngOnChanges(_changes:SimpleChanges){const t=this.trip;const key=this.accepted(t)?`${t.id}:${t.passengerId}`:'';if(key===this.loadedPhotoKey)return;this.clearPhoto();this.loadedPhotoKey=key;if(key)this.api.file(`/trips/${t.id}/passenger-photo`).then(blob=>{if(this.loadedPhotoKey===key)this.passengerPhotoUrl=URL.createObjectURL(blob)}).catch(()=>{})}
 private clearPhoto(){if(this.passengerPhotoUrl?.startsWith('blob:'))URL.revokeObjectURL(this.passengerPhotoUrl);this.passengerPhotoUrl=null}
 ngOnDestroy(){this.clearPhoto();if(this.waitTimer)clearInterval(this.waitTimer)}
}
