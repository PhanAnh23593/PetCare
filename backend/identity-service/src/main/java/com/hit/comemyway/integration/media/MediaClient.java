package com.hit.comemyway.integration.media;

import java.io.IOException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "media-service", url = "${media.service.url}",
    configuration = MediaFeignConfiguration.class)
public interface MediaClient {
  @PostMapping(value = "/internal/media/upload", consumes = "application/octet-stream")
  String upload(@RequestBody byte[] bytes) throws IOException;
}
