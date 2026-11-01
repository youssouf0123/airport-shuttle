import { Component, inject, computed, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonButton, IonIcon, IonSegment, IonSegmentButton, IonLabel } from '@ionic/angular/standalone';
import { Api, Trip, PlaceSuggestion, isActive, statusLabel } from '../../shared/api';
import { I18n, TranslatePipe, MoneyPipe, StatusPipe } from '../../shared/i18n';
import { TripMap } from '../../shared/map';
import { TripDetail } from '../../shared/trip-detail';
@Component({selector:'passenger-view',standalone:true,imports:[CommonModule,FormsModule,IonButton,IonIcon,IonSegment,IonSegmentButton,IonLabel,TripMap,TripDetail,TranslatePipe,MoneyPipe,StatusPipe],template:`
 <div class="page-heading"><div><div class="eyebrow">{{'passenger'|tr|uppercase}}</div><h1>{{'yourNextRide'|tr}}</h1></div><ion-button fill="outline" (click)="notifications()" [disabled]="api.busy()"><ion-icon slot="start" name="notifications-outline" />{{'arrivalAlerts'|tr}}</ion-button></div>
 <ion-segment [value]="tab()" (ionChange)="tab.set($any($event.detail.value))"><ion-segment-button value="book"><ion-label>{{'booking'|tr}}</ion-label></ion-segment-button><ion-segment-button value="live"><ion-label>{{'myRides'|tr}} ({{active().length}})</ion-label></ion-segment-button><ion-segment-button value="history"><ion-label>{{'history'|tr}}</ion-label></ion-segment-button></ion-segment>
 <div class="arrival-notice" *ngFor="let n of unread()"><ion-icon name="notifications-outline" /><span>{{n.message}}</span><button class="icon-button" aria-label="Dismiss arrival alert" (click)="read(n.id)"><ion-icon name="close-outline" /></button></div>
 <div class="booking-grid" *ngIf="tab()==='book'">
  <form class="booking-form" ngNativeValidate (ngSubmit)="book()"><h2>{{'wherePickup'|tr}}</h2>
   <label>{{'pickupSpot'|tr}}<input aria-label="{{'pickupSpot'|tr}}" name="pickupSearch" [(ngModel)]="pickupQuery" (ngModelChange)="searchPlaces($event,'pickup')" autocomplete="off" required><div class="place-suggestions" *ngIf="focused==='pickup' && suggestions.length"><button type="button" *ngFor="let place of suggestions" (click)="choosePlace(place,'pickup')">{{place.name}}<small>{{place.detail}}</small></button></div></label>
   <p class="field-detail">{{pickup()?.detail}}</p>
   <label>{{'destination'|tr}}<input aria-label="{{'destination'|tr}}" name="destinationSearch" [(ngModel)]="destinationQuery" (ngModelChange)="searchPlaces($event,'destination')" autocomplete="off" required><div class="place-suggestions" *ngIf="focused==='destination' && suggestions.length"><button type="button" *ngFor="let place of suggestions" (click)="choosePlace(place,'destination')">{{place.name}}<small>{{place.detail}}</small></button></div></label>
   <div class="form-row"><label>{{'flightNumber'|tr}}<input name="flight" [(ngModel)]="booking.flight" placeholder="e.g. DL 204" required maxlength="20"></label><label>{{'pickupDate'|tr}}<input name="pickupAt" type="datetime-local" [(ngModel)]="pickupAt" [min]="minDate" required></label></div>
   <hr><h2>{{'whoComing'|tr}}</h2>
   <div class="form-row"><label>{{'passengers'|tr}}<input name="passengers" type="number" [(ngModel)]="booking.passengers" min="1" max="8" required></label><label>{{'luggage'|tr}}<input name="bags" type="number" [(ngModel)]="booking.bags" min="0" max="12" required></label></div>
   <label>{{'pickupNotes'|tr}} <span class="optional">{{'optional'|tr}}</span><textarea name="notes" [(ngModel)]="booking.notes" maxlength="500" placeholder="{{'pickupPlaceholder'|tr}}" rows="2"></textarea></label>
   <div class="fare"><span>{{'estimatedFare'|tr}}</span><strong>{{4500+(booking.passengers-1)*800 | money}}</strong></div>
   <ion-button type="submit" expand="block" [disabled]="api.busy() || !api.online()">{{api.busy() ? ('requesting'|tr) : ('requestRide'|tr)}}<ion-icon slot="end" name="arrow-forward-outline" /></ion-button>
  </form>
  <section class="booking-map"><trip-map [pickup]="pickup()" [destination]="destination()" /><div class="map-foot"><span><ion-icon name="location-outline" />{{pickup()?.name}}</span><ion-icon name="arrow-forward-outline" /><span>{{destination()?.name}}</span></div></section>
 </div>
 <ng-container *ngIf="tab()!=='book'">
  <div class="trip-workspace" *ngIf="visible().length; else noTrips">
   <aside class="trip-list"><button class="trip-row" *ngFor="let t of visible()" [class.selected]="selected()?.id===t.id" (click)="selectedId.set(t.id)"><div><span class="reference">AS-{{t.id.toString().padStart(4,'0')}}</span><span class="status" [attr.data-status]="t.status">{{t.status|status}}</span></div><strong>{{t.pickup.name}}</strong><span>{{'to'|tr}} {{t.destination.name}}</span><small>{{t.pickupAt | date:'EEE, MMM d · h:mm a'}}</small></button></aside>
   <section *ngIf="selected() as t" class="selected-trip"><div class="tracking-heading"><div><h2>{{t.status|status}}</h2><p *ngIf="t.etaMinutes !== null && fresh(t) && ['EN_ROUTE','ARRIVING'].includes(t.status)">{{t.etaMinutes}} {{'minutesToPickup'|tr}}</p><p *ngIf="t.locationAt && !fresh(t)">{{'lastLocationAt'|tr}} {{t.locationAt | date:'h:mm a'}}</p><p *ngIf="!t.locationAt">{{t.driverName ? ('locationAppears'|tr) : ('findingDriver'|tr)}}</p></div></div><trip-map [trip]="t" /><trip-detail [trip]="t" />
    <div class="actions"><a *ngIf="t.status==='COMPLETED'" class="download-link" [href]="'/api/trips/'+t.id+'/receipt?workspace=PASSENGER&language='+i18n.language()"><ion-icon name="download-outline" />{{'downloadReceipt'|tr}}</a><ion-button *ngIf="cancellable(t)" color="danger" fill="outline" (click)="cancel(t)" [disabled]="api.busy()">{{'cancelRide'|tr}}</ion-button></div>
   </section>
  </div>
  <ng-template #noTrips><div class="empty"><ion-icon name="car-outline" /><h2>{{tab()==='history' ? ('noPastTrips'|tr) : ('noUpcoming'|tr)}}</h2><ion-button (click)="tab.set('book')">{{'bookRide'|tr}}</ion-button></div></ng-template>
 </ng-container>`})
export class Passenger {
 api=inject(Api);i18n=inject(I18n);tab=signal('book');selectedId=signal<number|null>(null);label=statusLabel;
 booking={pickupId:4,destinationId:1,flight:'',passengers:1,bags:1,notes:''};pickupQuery='';destinationQuery='';suggestions:PlaceSuggestion[]=[];focused='';private searchTimer?:ReturnType<typeof setTimeout>;requestKey=crypto.randomUUID();
 minDate=this.localDate(new Date(Date.now()+120000));pickupAt=this.localDate(new Date(Date.now()+3600000));
 active=computed(()=>this.api.trips().filter(isActive));unread=computed(()=>this.api.notices().filter(n=>!n.readAt));
 visible=computed(()=>this.api.trips().filter(t=>this.tab()==='history'?!isActive(t):isActive(t)));
 selected=computed<Trip|null>(()=>this.visible().find(t=>t.id===this.selectedId())||this.visible()[0]||null);
 constructor(){const id=Number(new URLSearchParams(window.location.search).get('trip'));if(id>0){this.tab.set('live');this.selectedId.set(id)}effect(()=>{const locations=this.api.locations();if(locations.length&&!this.pickupQuery)this.pickupQuery=locations.find(l=>l.id===this.booking.pickupId)?.name||'';if(locations.length&&!this.destinationQuery)this.destinationQuery=locations.find(l=>l.id===this.booking.destinationId)?.name||''})}
 searchPlaces(query:string,kind:string){this.focused=kind;clearTimeout(this.searchTimer);if(query.trim().length<2){this.suggestions=[];return}this.searchTimer=setTimeout(async()=>{try{this.suggestions=await this.api.request<PlaceSuggestion[]>('/places/search?q='+encodeURIComponent(query))}catch{this.suggestions=[]}},300)}
 async choosePlace(place:PlaceSuggestion,kind:string){const location=await this.api.request<any>('/locations/resolve','POST',place);if(kind==='pickup'){this.booking.pickupId=location.id;this.pickupQuery=location.name}else{this.booking.destinationId=location.id;this.destinationQuery=location.name}this.suggestions=[];this.focused='';this.pickupChanged()}
 pickupChanged(){if(!this.destinations().some(l=>l.id===this.booking.destinationId))this.booking.destinationId=this.destinations()[0]?.id||0}
 localDate(date:Date){return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16)}
 pickup(){return this.api.locations().find(l=>l.id===this.booking.pickupId)||null}
 destinations(){return this.api.locations().filter(l=>l.airport!==this.pickup()?.airport)}
 destination(){return this.api.locations().find(l=>l.id===this.booking.destinationId)||null}
 async book(){if(!this.destinations().some(l=>l.id===this.booking.destinationId)){this.api.error.set('Choose a destination on the other side of your airport connection.');return}await this.api.act(async()=>{const trip=await this.api.request<Trip>('/trips','POST',{...this.booking,pickupAt:new Date(this.pickupAt).toISOString(),requestKey:this.requestKey});this.requestKey=crypto.randomUUID();this.selectedId.set(trip.id);this.tab.set('live')},'Ride requested. Dispatch will assign your driver.')}
 cancellable(t:Trip){return !['BOARDING','IN_TRANSIT','COMPLETED','CANCELLED'].includes(t.status)}
 async cancel(t:Trip){if(!confirm('Cancel this ride?'))return;await this.api.act(()=>this.api.request('/trips/'+t.id+'/status','PUT',{status:'CANCELLED',version:t.version}),'Ride cancelled.')}
 fresh(t:Trip){return !!t.locationAt&&Date.now()-Date.parse(t.locationAt)<120000}
 notifications(){this.api.act(()=>this.api.enablePush(),'Arrival alerts enabled for this browser.')}
 read(id:number){this.api.act(()=>this.api.request('/notifications/'+id+'/read','PUT'), '')}
}
