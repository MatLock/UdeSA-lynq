package com.lynq.bff.config;

import com.lynq.bff.ratelimit.RateLimitInterceptor;
import com.lynq.bff.ratelimit.RateLimitProperties;
import com.lynq.bff.ratelimit.RateLimited;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class OpenApiConfig {

    static final String TOO_MANY_REQUESTS = "429";

    private static final String TOO_MANY_REQUESTS_DESCRIPTION = "The caller used up their %s "
        + "quota: %d requests per minute and %d per day, shared by every endpoint of that tier. "
        + "Retry-After says how many seconds to wait; the reason is RATE_LIMIT_EXCEEDED.";
    private static final String RETRY_AFTER_DESCRIPTION =
        "Seconds to wait before the quota lets the caller through again.";
    private static final String REMAINING_DESCRIPTION =
        "Requests the caller has left in the current minute for this endpoint's tier.";

    @Bean
    public OpenAPI lynqBffOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("lynq-bff")
                .version("v1")
                .description("Backend-for-frontend gateway for the Lynq platform. It is the only "
                    + "service the frontend talks to: it verifies the access token's signature and "
                    + "then passes requests and responses straight through to lynq-backend, lynq-llm "
                    + "and lynq-file-storage, whose APIs sit behind a `/dmz` prefix. Every request "
                    + "must carry the `Authorization` and `lynq-request-uuid` headers. Endpoints "
                    + "that reach lynq-llm or lynq-agent are rate limited per user and answer 429 "
                    + "once the caller's quota runs out."));
    }

    @Bean
    public OperationCustomizer rateLimitedOperationCustomizer(RateLimitProperties properties) {
        return (operation, handlerMethod) -> {
            RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
            if (rateLimited == null) {
                return operation;
            }
            RateLimitProperties.Limit limit = properties.limitOf(rateLimited.value());

            ApiResponses responses = operation.getResponses() == null
                ? new ApiResponses()
                : operation.getResponses();
            responses.forEach((status, response) -> {
                if (status.startsWith("2")) {
                    response.addHeaderObject(RateLimitInterceptor.REMAINING_HEADER,
                        integerHeader(REMAINING_DESCRIPTION));
                }
            });
            responses.addApiResponse(TOO_MANY_REQUESTS, new ApiResponse()
                .description(String.format(TOO_MANY_REQUESTS_DESCRIPTION,
                    rateLimited.value(), limit.perMinute(), limit.perDay()))
                .addHeaderObject(HttpHeaders.RETRY_AFTER, integerHeader(RETRY_AFTER_DESCRIPTION)));
            return operation.responses(responses);
        };
    }

    private static Header integerHeader(String description) {
        return new Header().description(description).schema(new IntegerSchema());
    }
}
