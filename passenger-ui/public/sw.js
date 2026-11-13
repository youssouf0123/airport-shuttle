self.addEventListener('push',event=>{
 let data={message:'Your shuttle is arriving.',tripId:0};
 try{data=event.data.json()}catch{}
 event.waitUntil(self.registration.showNotification('Airport Shuttle',{body:data.message,tag:'arrival-'+data.tripId,data:{url:'/passenger/?trip='+data.tripId},requireInteraction:true}));
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 event.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(async clients=>{
  const existing=clients.find(client=>new URL(client.url).pathname.startsWith('/passenger/'));
  if(existing){await existing.navigate(event.notification.data.url);return existing.focus()}
  return self.clients.openWindow(event.notification.data.url);
 }));
});
