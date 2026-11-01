import { Component, ElementRef, ViewChild, Input, AfterViewInit, OnChanges, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';
import { Trip, Location } from './api';
@Component({selector:'trip-map',standalone:true,imports:[CommonModule],template:`<div class="map-wrap"><div #canvas class="map-canvas" role="img" aria-label="Pickup, destination and live shuttle location"></div><div class="map-caption">{{caption()}}</div><div class="map-legend"><span><b class="legend-p">P</b> Pickup</span><span><b class="legend-d">D</b> Destination</span><span *ngIf="trip?.driverLat != null"><b class="legend-s">🚐</b> Shuttle</span></div></div>`})
export class TripMap implements AfterViewInit,OnChanges,OnDestroy {
 @ViewChild('canvas') canvas!:ElementRef;@Input() trip:Trip|null=null;@Input() pickup:Location|null=null;@Input() destination:Location|null=null;
 caption=signal('');private map?:L.Map;private layer?:L.LayerGroup;private abort?:AbortController;private signature='';private selected='';private resize?:ResizeObserver;private animation?:number;private lastVehicle?:{lat:number;lng:number};private lastTripId?:number;
 ngAfterViewInit(){this.map=L.map(this.canvas.nativeElement,{zoomControl:true}).setView([12.64,-8.00],12);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',maxZoom:19}).addTo(this.map);this.layer=L.layerGroup().addTo(this.map);this.resize=new ResizeObserver(()=>this.map?.invalidateSize());this.resize.observe(this.canvas.nativeElement);this.draw()}
 ngOnChanges(){this.draw()}
 private async draw(){
  if(!this.map||!this.layer)return;
  const p=this.trip?.pickup||this.pickup,d=this.trip?.destination||this.destination;
  const t=this.trip;const signature=JSON.stringify([p?.id,d?.id,t?.via?.id,t?.driverLat,t?.driverLng,t?.status]);if(signature===this.signature)return;this.signature=signature;this.abort?.abort();this.abort=new AbortController();this.layer.clearLayers();if(this.lastTripId!==t?.id){this.lastTripId=t?.id;this.lastVehicle=undefined}
  const points:L.LatLngTuple[]=[];
  const marker=(loc:{lat:number;lng:number},label:string,title:string)=>{points.push([loc.lat,loc.lng]);const el=document.createElement('span');el.textContent=title;L.marker([loc.lat,loc.lng],{icon:L.divIcon({className:'map-pin pin-'+label,html:label,iconSize:[30,30],iconAnchor:[15,30]}),title}).bindPopup(el).addTo(this.layer!)};
  if(p)marker(p,'P',p.name);if(d)marker(d,'D',d.name);if(t?.via)marker(t.via,'V',t.via.name);
  const vehicle=t?.driverLat!=null&&t?.driverLng!=null?{lat:t.driverLat,lng:t.driverLng}:null;
  if(vehicle){
   points.push([vehicle.lat,vehicle.lng]);const from=this.lastVehicle||vehicle;
   const vehicleMarker=L.marker([from.lat,from.lng],{icon:L.divIcon({className:'vehicle-icon',html:'<span class="vehicle-marker" aria-label="Live shuttle">🚐</span>',iconSize:[42,42],iconAnchor:[21,21]}),title:t?.vehicle||'Shuttle'}).addTo(this.layer);
   this.animateVehicle(vehicleMarker,from,vehicle);this.lastVehicle=vehicle;
  }else this.lastVehicle=undefined;
  const selected=`${t?.id}-${p?.id}-${d?.id}-${t?.via?.id}`;if(points.length&&selected!==this.selected){this.map.fitBounds(L.latLngBounds(points),{padding:[45,45],maxZoom:14});this.selected=selected}
  if(!p||!d){this.caption.set('Bamako / BKO');return}
  const approach=vehicle&&['EN_ROUTE','ARRIVING','BOARDING'].includes(t?.status||'');
  const route=approach?[vehicle!,p]:[vehicle&&t?.status==='IN_TRANSIT'?vehicle:p,...(t?.via?[t.via]:[]),d];
  this.caption.set('Finding road route...');
  try{const res=await fetch('https://router.project-osrm.org/route/v1/driving/'+route.map(x=>`${x.lng},${x.lat}`).join(';')+'?overview=full&geometries=geojson',{signal:AbortSignal.any([this.abort.signal,AbortSignal.timeout(10000)])});if(!res.ok)throw new Error();const data=await res.json();if(!data.routes?.length)throw new Error();if(this.signature!==signature)return;const r=data.routes[0];L.polyline(r.geometry.coordinates.map((c:number[])=>[c[1],c[0]]),{color:'#087c66',weight:5,opacity:.85}).addTo(this.layer);this.caption.set(`${(r.distance/1609.34).toFixed(1)} mi · ${Math.ceil(r.duration/60)} min road estimate${approach?' to pickup':''}`)}catch{if(this.signature===signature)this.caption.set('Road route unavailable. Pickup and destination are shown.')}
 }
 private animateVehicle(marker:L.Marker,from:{lat:number;lng:number},to:{lat:number;lng:number}){if(this.animation)cancelAnimationFrame(this.animation);if(from.lat===to.lat&&from.lng===to.lng)return;const started=performance.now(),duration=700;const step=(now:number)=>{const progress=Math.min(1,(now-started)/duration);const ease=progress<.5?2*progress*progress:1-Math.pow(-2*progress+2,2)/2;marker.setLatLng([from.lat+(to.lat-from.lat)*ease,from.lng+(to.lng-from.lng)*ease]);if(progress<1)this.animation=requestAnimationFrame(step)};this.animation=requestAnimationFrame(step)}
 ngOnDestroy(){this.abort?.abort();if(this.animation)cancelAnimationFrame(this.animation);this.resize?.disconnect();this.map?.remove()}
}
