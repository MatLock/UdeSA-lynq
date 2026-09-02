package com.lynq.bff.config;

import feign.codec.Encoder;
import org.springframework.context.annotation.Bean;

public class DmzPassThroughFeignConfig {

  @Bean
  public Encoder dmzPassThroughEncoder() {
    return (body, bodyType, template) -> {
      if (body instanceof byte[] bytes) {
        template.body(bytes, null);
      }
    };
  }
}
