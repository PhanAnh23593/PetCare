package com.hit.media;

import com.hit.comemyway.service.ImageUploadService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalMediaController {
  private final ImageUploadService images;

  public InternalMediaController(ImageUploadService images) {
    this.images = images;
  }

  @PostMapping(value = "/internal/media/upload",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
  public String upload(@RequestBody(required = false) byte[] bytes) throws IOException {
    return images.uploadImage(new UploadBytes(bytes == null ? new byte[0] : bytes));
  }

  @GetMapping("/internal/health")
  public String health() {
    return "UP";
  }
}
