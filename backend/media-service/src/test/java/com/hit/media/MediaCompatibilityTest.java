package com.hit.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"media.internal.token=test-internal-token",
    "cloudinary.cloud-name=test", "cloudinary.api-key=test", "cloudinary.api-secret=test"})
@AutoConfigureMockMvc
class MediaCompatibilityTest {
  @Autowired
  MockMvc mvc;
  @Autowired
  ObjectMapper json;
  @MockitoBean
  Cloudinary cloudinary;
  Uploader uploader;

  @BeforeEach
  void prepare() {
    uploader = org.mockito.Mockito.mock(Uploader.class);
    when(cloudinary.uploader()).thenReturn(uploader);
  }

  @Test
  void preservesOriginalBytesOptionsAndHttpUrl() throws Exception {
    byte[] bytes = {0, 1, 2, -1, 10, 13};
    when(uploader.upload(any(), anyMap())).thenReturn(
        Map.of("url", "http://image.invalid/a.jpg", "secure_url", "https://image.invalid/a.jpg"));
    var response =
        mvc.perform(post("/internal/media/upload").header("X-Media-Token", "test-internal-token")
            .contentType("application/octet-stream").content(bytes)).andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).isEqualTo("http://image.invalid/a.jpg");
    var data = ArgumentCaptor.forClass(Object.class);
    var options = ArgumentCaptor.forClass(Map.class);
    verify(uploader).upload(data.capture(), options.capture());
    assertThat((byte[]) data.getValue()).isEqualTo(bytes);
    assertThat(options.getValue()).isEmpty();
  }

  @Test
  void rejectsMissingAndIncorrectInternalTokensBeforeUploading() throws Exception {
    for (String token : new String[] {"", "incorrect"}) {
      assertThat(mvc
          .perform(post("/internal/media/upload").header("X-Media-Token", token)
              .contentType("application/octet-stream").content(new byte[] {1}))
          .andReturn().getResponse().getStatus()).isEqualTo(401);
    }
    verifyNoInteractions(uploader);
  }

  @Test
  void retainsInvalidArgumentMessageAndSeparatesProviderFailure() throws Exception {
    when(uploader.upload(any(), anyMap())).thenThrow(new IllegalArgumentException("Invalid image"));
    var bad = mvc
        .perform(post("/internal/media/upload").header("X-Media-Token", "test-internal-token")
            .contentType("application/octet-stream").content(new byte[] {1}))
        .andReturn().getResponse();
    assertThat(bad.getStatus()).isEqualTo(400);
    assertThat(json.readTree(bad.getContentAsString()).path("message").asText())
        .isEqualTo("Invalid image");
    org.mockito.Mockito.doThrow(new IOException("provider detail must stay internal"))
        .when(uploader).upload(any(), anyMap());
    var failed = mvc
        .perform(post("/internal/media/upload").header("X-Media-Token", "test-internal-token")
            .contentType("application/octet-stream").content(new byte[] {1}))
        .andReturn().getResponse();
    assertThat(failed.getStatus()).isEqualTo(500);
    assertThat(failed.getContentAsString()).doesNotContain("provider detail");
  }
}
