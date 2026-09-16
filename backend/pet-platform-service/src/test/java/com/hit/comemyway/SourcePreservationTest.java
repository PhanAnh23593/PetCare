package com.hit.comemyway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SourcePreservationTest {
  @Test
  void everyOriginalProductionClassIsPreservedInItsOwningService() throws Exception {
    Path root = Path.of("..").toAbsolutePath().normalize();
    var manifest = Files.readAllLines(root.resolve("compatibility/source-sha256.tsv"));
    assertThat(manifest).hasSize(114);
    for (String row : manifest) {
      String[] columns = row.split("\t");
      String relative = columns[0].replace('\\', '/');
      boolean movedToMedia = relative.equals("com/hit/comemyway/service/ImageUploadService.java")
          || relative.equals("com/hit/comemyway/config/CloudinaryConfig.java");
      Path source = root.resolve(movedToMedia ? "media-service" : "pet-platform-service")
          .resolve("src/main/java").resolve(relative);
      String normalized = Files.readString(source).replace("\r\n", "\n");
      String actual = HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
      assertThat(actual).as(relative).isEqualToIgnoringCase(columns[1]);
    }
  }
}
