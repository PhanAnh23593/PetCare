package com.hit.media;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MediaErrors {
  public record Failure(String kind, String message) {}

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Failure> invalidArgument(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(new Failure("INVALID_ARGUMENT", exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Failure> uploadFailure(Exception exception) {
    return ResponseEntity.internalServerError().body(new Failure("UPLOAD_FAILED", "Upload failed"));
  }
}
