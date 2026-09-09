package com.lynq.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableFeignClients
public class LynqBffApplication {

	public static void main(String[] args) {
		SpringApplication.run(LynqBffApplication.class, args);
	}
}
