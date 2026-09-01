package com.lynq.bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.filter.AuthHeaderExistenceFilter;
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.filter.RequestUuidFilter;
import com.lynq.bff.security.JwtSignatureVerifier;
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
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtSignatureFilter> createJwtSignatureFilter(
        JwtSignatureVerifier jwtSignatureVerifier, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtSignatureFilter(jwtSignatureVerifier, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        return registration;
    }
}
