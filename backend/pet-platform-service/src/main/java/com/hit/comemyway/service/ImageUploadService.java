package com.hit.comemyway.service;

import com.hit.comemyway.integration.media.MediaClient;
import feign.FeignException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Transport adapter for the unchanged public ImageController. */
@Service
@RequiredArgsConstructor
public class ImageUploadService {
  private final MediaClient media;

  public String uploadImage(MultipartFile file) throws IOException {
    try {
      return media.upload(file.getBytes());
    } catch (FeignException exception) {
      throw new IOException("Media upload unavailable", exception);
    }
  }
}
