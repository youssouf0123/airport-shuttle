import { Component, Input, OnChanges, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';
import { Api,Trip,TripEvent,statusLabel } from './api';
import { TranslatePipe, MoneyPipe, StatusPipe } from './i18n';
@Component({selector:'trip-detail',standalone:true,imports:[CommonModule,IonIcon,TranslatePipe,MoneyPipe,StatusPipe],styles:[`.wait-clock{display:flex;justify-content:space-between;gap:12px;padding:12px 14px;margin:12px 0;border-radius:10px;background:var(--soft,#eef5f3);color:var(--text,#20312e)}.wait-clock strong{font-variant-numeric:tabular-nums}`],template:`
 <ng-container *ngIf="trip as t"><div class="detail-title"><span class="reference">AS-{{t.id.toString().padStart(4,'0')}}</span><span class="status" [attr.data-status]="t.status">{{t.status|status}}</span></div>
 <div class="route-summary"><div><span class="route-dot"></span><small>{{'pickup'|tr}}</small><strong>{{t.pickup.name}}</strong><span>{{t.pickup.detail}}</span></div><div *ngIf="t.via"><span class="route-dot via"></span><small>{{'extraStop'|tr}}</small><strong>{{t.via.name}}</strong></div><div><span class="route-dot destination"></span><small>{{'destination'|tr}}</small><strong>{{t.destination.name}}</strong><span>{{t.destination.detail}}</span></div></div>
 <div class="facts"><div><ion-icon name="calendar-outline" /><span>{{t.pickupAt | date:'MMM d, h:mm a'}}</span></div><div><ion-icon name="airplane-outline" /><span>{{t.flight}}</span></div><div><ion-icon name="people-outline" /><span>{{t.passengers}} {{'passengers'|tr|lowercase}}</span></div><div><ion-icon name="bag-handle-outline" /><span>{{t.bags}} {{'luggage'|tr|lowercase}}</span></div></div>
 <div class="wait-clock" *ngIf="t.status==='ARRIVING' && t.arrivedAt"><span>{{'waitingAtPickup'|tr}}</span><strong>{{waitDuration(t)}}</strong></div>
 <div class="driver-info" *ngIf="t.driverName"><ion-icon name="car-outline" /><div><strong>{{t.driverName}}</strong><span>{{t.vehicle}}</span></div></div>
 <p class="note" *ngIf="t.notes"><strong>{{'passengerNote'|tr}}</strong>{{t.notes}}</p><p class="note" *ngIf="t.dispatchNotes"><strong>{{'dispatchNote'|tr}}</strong>{{t.dispatchNotes}}</p>
 <div class="fare"><span>{{'estimatedFare'|tr}}</span><strong>{{t.fareCents | money:t.fareXof}}</strong></div>
 <details class="activity" (toggle)="loadEvents($event)"><summary>{{'tripActivity'|tr}}</summary><p *ngIf="eventError()">{{'couldNotLoad'|tr}}</p><ol><li *ngFor="let e of events()"><strong>{{e.detail}}</strong><small>{{e.createdAt | date:'MMM d, h:mm a'}} · {{e.actor}}</small></li></ol></details>
 </ng-container>`})
 export class TripDetail implements OnChanges,OnInit,OnDestroy {
 @Input({required:true}) trip!:Trip;api=inject(Api);label=statusLabel;events=signal<TripEvent[]>([]);eventError=signal('');open=false;
 private waitTimer?:ReturnType<typeof setInterval>;private now=signal(Date.now());
 ngOnInit(){this.waitTimer=setInterval(()=>this.now.set(Date.now()),1000)}
 ngOnDestroy(){if(this.waitTimer)clearInterval(this.waitTimer)}
 waitDuration(t:Trip){const seconds=Math.max(0,t.waitSeconds??(t.arrivedAt?Math.floor((this.now()-Date.parse(t.arrivedAt))/1000):0));return `${Math.floor(seconds/60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}`}
 ngOnChanges(){if(this.open)this.fetchEvents()}
 loadEvents(event:Event){this.open=(event.target as HTMLDetailsElement).open;if(this.open)this.fetchEvents()}
 async fetchEvents(){try{const id=this.trip.id;const events=await this.api.request<TripEvent[]>(`/trips/${id}/events`);if(this.trip.id===id)this.events.set(events)}catch{this.eventError.set('Could not load activity.')}}
}
