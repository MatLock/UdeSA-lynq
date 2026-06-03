package com.lynq.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.iam.filter.AuthHeaderExistenceFilter;
import com.lynq.iam.filter.AuthHeaderValidationFilter;
import com.lynq.iam.filter.RequestUuidFilter;
import com.lynq.iam.security.JWTService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestUuidFilter> createRequestUuidFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<RequestUuidFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestUuidFilter(objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthHeaderExistenceFilter> createAuthHeaderExistenceFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<AuthHeaderExistenceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthHeaderExistenceFilter(objectMapper));
        registration.addUrlPatterns("/lynq-iam/auth/validate", "/lynq-iam/auth/refresh", "/lynq-iam/auth/update-password");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthHeaderValidationFilter> createAuthHeaderValidationFilter(ObjectMapper objectMapper, JWTService jwtService) {
        FilterRegistrationBean<AuthHeaderValidationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthHeaderValidationFilter(objectMapper, jwtService));
        registration.addUrlPatterns("/lynq-iam/auth/validate", "/lynq-iam/auth/update-password");
        registration.setOrder(2);
        return registration;
    }
}