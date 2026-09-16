package com.hit.comemyway.integration.media;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(clients = MediaClient.class)
public class MediaClientConfiguration {
}
