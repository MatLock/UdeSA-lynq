package com.lynq.filestorage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lynqFileStorageOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("lynq-file-storage")
                .version("v1")
                .description("File storage service for the Lynq platform. Stores file metadata in "
                    + "MySQL and hands out pre-signed S3 URLs for upload and download. Every request "
                    + "must carry the `lynq-request-uuid` header."));
    }
}
