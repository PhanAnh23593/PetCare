package com.hit.comemyway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class ComemywayApplication {

  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    SpringApplication.run(ComemywayApplication.class, args);
  }

}
