package com.lynq.filestorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class LynqFileStorageApplication {

	public static void main(String[] args) {
		SpringApplication.run(LynqFileStorageApplication.class, args);
	}

}
