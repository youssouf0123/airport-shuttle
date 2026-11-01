package com.airportshuttle;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.*;
import java.util.Map;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> expected(ResponseStatusException ex) {return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"Request failed.":ex.getReason()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException ex) {var e=ex.getBindingResult().getFieldErrors().getFirst();return ResponseEntity.badRequest().body(Map.of("message",e.getField()+": "+e.getDefaultMessage()));}
 @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> malformed(Exception ex) {return ResponseEntity.badRequest().body(Map.of("message","Check the request values and try again."));}
 @ExceptionHandler({ConcurrencyFailureException.class,DuplicateKeyException.class}) ResponseEntity<?> conflict(Exception ex) {return ResponseEntity.status(409).body(Map.of("message","This record changed. Refresh and try again."));}
}
