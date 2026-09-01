package com.lynq.bff.config;

import feign.Client;
import feign.codec.Encoder;
import feign.hc5.ApacheHttp5Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

  @Bean
  public Client dmzFeignClient() {
    return new ApacheHttp5Client();
  }

  @Bean
  public Encoder dmzPassThroughEncoder() {
    return (body, bodyType, template) -> {
      if (body instanceof byte[] bytes) {
        template.body(bytes, null);
      }
    };
  }
}
