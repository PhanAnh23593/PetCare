package com.hit.media;

import com.hit.comemyway.config.CloudinaryConfig;
import com.hit.comemyway.service.ImageUploadService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({CloudinaryConfig.class, ImageUploadService.class})
public class MediaApplication {
  public static void main(String[] args) {
    SpringApplication.run(MediaApplication.class, args);
  }
}
