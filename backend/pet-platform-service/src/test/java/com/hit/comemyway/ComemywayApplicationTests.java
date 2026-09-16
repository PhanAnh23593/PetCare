package com.hit.comemyway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.hit.comemyway.config.FirebaseConfig;

@SpringBootTest
@ActiveProfiles("test")
class ComemywayApplicationTests {

  @MockitoBean
  private FirebaseConfig firebaseConfig;

  @Test
  void contextLoads() {}

}
