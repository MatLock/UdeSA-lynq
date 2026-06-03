package com.lynq.iam.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.iam.controller.response.ErrorRestResponse;
import com.lynq.iam.security.JWTService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AuthHeaderValidationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_TOKEN_ERROR = "Invalid or expired access token";

    private final ObjectMapper objectMapper;
    private final JWTService jwtService;

    public AuthHeaderValidationFilter(ObjectMapper objectMapper, JWTService jwtService) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        String token = authHeader.startsWith(BEARER_PREFIX) ? authHeader.substring(7) : authHeader;

        if (!jwtService.isAccessTokenValid(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorRestResponse<Void> errorResponse = new ErrorRestResponse<>(null, INVALID_TOKEN_ERROR);
            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }
}