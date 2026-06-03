package com.lynq.iam.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.iam.controller.response.ErrorRestResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.MDC;

import java.io.IOException;


public class RequestUuidFilter extends OncePerRequestFilter {

    private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
    private static final String MDC_REQUEST_ID = "requestId";

    private static final String[] WHITELISTED_PATH_PREFIXES = {
        "/swagger-ui",
        "/v3/api-docs",
        "/swagger-resources",
        "/webjars"
    };

    private final ObjectMapper objectMapper;

    public RequestUuidFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if ("/swagger-ui.html".equals(path)) {
            return true;
        }
        for (String prefix : WHITELISTED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUuid = request.getHeader(REQUEST_UUID_HEADER);

        if (requestUuid == null || requestUuid.isBlank()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorRestResponse<Void> errorResponse = new ErrorRestResponse<>(null, "Missing required header");
            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }
        MDC.put(MDC_REQUEST_ID, requestUuid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}