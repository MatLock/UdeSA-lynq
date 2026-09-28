package com.lynq.filestorage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.filestorage.client.LynqIamClient;
import com.lynq.filestorage.filter.AuthHeaderExistenceFilter;
import com.lynq.filestorage.filter.IamAuthenticationFilter;
import com.lynq.filestorage.filter.RequestUuidFilter;
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
    public FilterRegistrationBean<IamAuthenticationFilter> createIamAuthenticationFilter(
        LynqIamClient lynqIamClient, ObjectMapper objectMapper) {
        FilterRegistrationBean<IamAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IamAuthenticationFilter(lynqIamClient, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        return registration;
    }
}
