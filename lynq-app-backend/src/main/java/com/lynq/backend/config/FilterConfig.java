package com.lynq.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.backend.client.LynqIamClient;
import com.lynq.backend.filter.AuthHeaderExistenceFilter;
import com.lynq.backend.filter.IamAuthenticationFilter;
import com.lynq.backend.filter.InternalTokenFilter;
import com.lynq.backend.filter.RequestUuidFilter;
import org.springframework.beans.factory.annotation.Value;
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
    public FilterRegistrationBean<InternalTokenFilter> createInternalTokenFilter(
        ObjectMapper objectMapper, @Value("${lynq.internal.token:}") String internalToken) {
        FilterRegistrationBean<InternalTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InternalTokenFilter(objectMapper, internalToken));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthHeaderExistenceFilter> createAuthHeaderExistenceFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<AuthHeaderExistenceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthHeaderExistenceFilter(objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<IamAuthenticationFilter> createIamAuthenticationFilter(
        LynqIamClient lynqIamClient, ObjectMapper objectMapper) {
        FilterRegistrationBean<IamAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IamAuthenticationFilter(lynqIamClient, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(3);
        return registration;
    }
}
