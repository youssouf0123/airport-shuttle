package com.airportshuttle;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.*;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PushDeliveryTest {
 @Test void encryptsAndRecordsSuccessfulDelivery()throws Exception{run(201);}
 @Test void removesExpiredBrowserSubscription()throws Exception{run(410);}
 @Test void failedDeliveryRemainsEligibleForRetry()throws Exception{run(503);}
 void run(int status)throws Exception{
  JdbcTemplate db=mock(JdbcTemplate.class);Push push=new Push(db,new ObjectMapper());
  KeyPairGenerator g=KeyPairGenerator.getInstance("ECDH","BC");g.initialize(new ECGenParameterSpec("secp256r1"));KeyPair server=g.generateKeyPair(),browser=g.generateKeyPair();
  String publicKey=Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey)browser.getPublic()));
  byte[] auth=new byte[16];new SecureRandom().nextBytes(auth);
  PushService sender=mock(PushService.class);ReflectionTestUtils.setField(push,"service",sender);
  when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(1);
  when(db.queryForList(anyString())).thenReturn(List.of(Map.of("notification_id",1L,"subscription_id",2L,"endpoint","https://fcm.googleapis.com/fcm/send/test-subscription","p256dh",publicKey,"auth",Base64.getUrlEncoder().withoutPadding().encodeToString(auth),"message","Your shuttle is 5 minutes away.","trip_id",3L)));
  when(sender.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(new BasicHttpResponse(HttpVersion.HTTP_1_1,status,"test")));
  push.deliver();ArgumentCaptor<Notification> notification=ArgumentCaptor.forClass(Notification.class);verify(sender).sendAsync(notification.capture());
  assertThat(notification.getValue().getTTL()).isEqualTo(300);
  assertThat(new ObjectMapper().readTree(notification.getValue().getPayload()).get("tripId").asLong()).isEqualTo(3);
  var encrypted=new PushService(server,"mailto:test@example.com").preparePost(notification.getValue(),Encoding.AES128GCM);
  assertThat(encrypted.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");assertThat(encrypted.getFirstHeader("Authorization").getValue()).startsWith("vapid ");
  if(status==201)verify(db).update("UPDATE push_deliveries SET delivered=true WHERE notification_id=? AND subscription_id=?",1L,2L);
  else if(status==410)verify(db).update("DELETE FROM push_subscriptions WHERE id=?",2L);
  else verify(db,never()).update(eq("UPDATE push_deliveries SET delivered=true WHERE notification_id=? AND subscription_id=?"),any(),any());
 }
}
