import { Injectable, InjectionToken, inject, signal, effect } from '@angular/core';
export const ROLE = new InjectionToken<string>('role');
export interface User { id:number;name:string;email:string;phone:string|null;role:string;vehicle:string;vehicleId:number|null;vehicleLabel:string|null;seats:number;luggageCapacity:number }
export interface Vehicle { id:number;year:number;make:string;model:string;color:string;doors:string;seatbelts:number;wheelchairRamp:boolean;licensePlate:string|null;vin:string|null;engineType:string|null;mileage:number|null;active:boolean;driverId:number|null;driverName:string|null;driverPhone:string|null }
export interface VehicleReference { years:number[];makes:string[];models:string[];engineTypes:string[] }
export interface Location {id:number;name:string;detail:string;lat:number;lng:number;airport:boolean}
export interface PlaceSuggestion {name:string;detail:string;lat:number;lng:number;airport:boolean}
export interface Trip {id:number;passengerId:number;passengerName:string;driverId:number|null;driverName:string|null;vehicle:string|null;pickup:Location;destination:Location;via:Location|null;flight:string;pickupAt:string;passengers:number;bags:number;notes:string;dispatchNotes:string;status:string;fareCents:number;version:number;driverLat:number|null;driverLng:number|null;locationAt:string|null;etaMinutes:number|null;createdAt:string;updatedAt:string}
export interface Notice {id:number;tripId:number;message:string;createdAt:string;readAt:string|null}
export interface TripEvent {id:number;status:string;detail:string;createdAt:string;actor:string}
export const isActive=(t:Trip)=>!['COMPLETED','CANCELLED'].includes(t.status);
export const statusLabel=(s:string)=>({REQUESTED:'Awaiting dispatch',ASSIGNED:'Awaiting driver',ACCEPTED:'Driver confirmed',EN_ROUTE:'On the way',ARRIVING:'Arriving',BOARDING:'Boarding',IN_TRANSIT:'On board',COMPLETED:'Completed',CANCELLED:'Cancelled'}[s]||s);
export class ApiError extends Error {constructor(message:string,public status:number){super(message)}}
@Injectable({providedIn:'root'})
export class Api {
 role=inject(ROLE);user=signal<User|null>(null);avatar=signal<string|null>(null);trips=signal<Trip[]>([]);locations=signal<Location[]>([]);drivers=signal<User[]>([]);vehicles=signal<Vehicle[]>([]);notices=signal<Notice[]>([]);
 error=signal('');success=signal('');busy=signal(false);online=signal(true);loaded=signal(false);private refreshing=false;
 private avatarKey(user:User){return `airport-shuttle-avatar:${this.role.toLowerCase()}:${user.id}`}
 constructor(){effect(()=>{const user=this.user();this.avatar.set(user?localStorage.getItem(this.avatarKey(user)):null)})}
 async request<T>(path:string,method='GET',body?:unknown):Promise<T>{
  const res=await fetch('/api'+path,{method,credentials:'same-origin',headers:{'Content-Type':'application/json','X-Shuttle-Request':'1','X-Shuttle-Role':this.role},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  if(!res.ok){let message='Request failed. Please try again.';try{message=(await res.json()).message||message}catch{}if(res.status===401&&this.user()){this.user.set(null);this.clearData()}throw new ApiError(message,res.status)}
  const text=await res.text();return (text?JSON.parse(text):null) as T;
 }
 async vehicleReference(year?:number|null,make?:string,model?:string){const params=new URLSearchParams();if(year)params.set('year',String(year));if(make)params.set('make',make);if(model)params.set('model',model);return this.request<VehicleReference>('/vehicle-reference'+(params.toString()?'?'+params.toString():''));}
 clearData(){this.trips.set([]);this.notices.set([]);this.drivers.set([]);this.loaded.set(false)}
 loadAvatar(user:User|null){this.avatar.set(user?localStorage.getItem(this.avatarKey(user)):null)}
 saveAvatar(dataUrl:string){const user=this.user();if(!user)return;localStorage.setItem(this.avatarKey(user),dataUrl);this.avatar.set(dataUrl)}
 removeAvatar(){const user=this.user();if(!user)return;localStorage.removeItem(this.avatarKey(user));this.avatar.set(null)}
 async refresh(){
  if(!this.user()||this.refreshing)return;this.refreshing=true;
  try{const [trips,notices,drivers,vehicles]=await Promise.all([this.request<Trip[]>('/trips'),this.request<Notice[]>('/notifications'),this.role==='DISPATCHER'?this.request<User[]>('/drivers'):Promise.resolve(this.drivers()),this.role==='DISPATCHER'?this.request<Vehicle[]>('/vehicles'):Promise.resolve(this.vehicles())]);if(this.user()){this.trips.set(trips);this.notices.set(notices);this.drivers.set(drivers);this.vehicles.set(vehicles);this.online.set(true);this.loaded.set(true)}}
  catch(e){this.online.set(false);if(e instanceof ApiError&&e.status===401)this.error.set('Your session expired. Please sign in again.')}
  finally{this.refreshing=false}
 }
 async load(){this.locations.set(await this.request<Location[]>('/locations'));if(this.role==='DISPATCHER')this.drivers.set(await this.request<User[]>('/drivers'));await this.refresh()}
 async act(work:()=>Promise<unknown>,message='Saved.'):Promise<boolean>{
  if(this.busy())return false;this.busy.set(true);this.error.set('');this.success.set('');
  try{await work();await this.refresh();this.success.set(message);return true}catch(e){this.error.set(e instanceof Error?e.message:'Connection failed. Try again.');return false}finally{this.busy.set(false)}
 }
 async enablePush(){
  if(!('serviceWorker' in navigator)||!('PushManager' in window))throw new Error('Push notifications are not supported in this browser. Trip alerts remain available here.');
  if(await Notification.requestPermission()!=='granted')throw new Error('Notifications are blocked. You can enable them in your browser settings.');
  await navigator.serviceWorker.register('/sw.js');const registration=await navigator.serviceWorker.ready;
  const {publicKey}=await this.request<{publicKey:string}>('/push/key');
  const key=Uint8Array.from(atob(publicKey.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));
  const subscription=await registration.pushManager.getSubscription()||await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:key});
  await this.request('/push/subscribe','POST',subscription.toJSON());
 }
}
