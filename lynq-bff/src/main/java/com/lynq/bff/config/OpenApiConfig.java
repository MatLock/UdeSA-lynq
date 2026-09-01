package com.lynq.bff.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lynqBffOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("lynq-bff")
                .version("v1")
                .description("Backend-for-frontend gateway for the Lynq platform. It is the only "
                    + "service the frontend talks to: it verifies the access token's signature and "
                    + "then passes requests and responses straight through to lynq-backend, lynq-ml "
                    + "and lynq-file-storage, whose APIs sit behind a `/dmz` prefix. Every request "
                    + "must carry the `Authorization` and `lynq-request-uuid` headers."));
    }
}
