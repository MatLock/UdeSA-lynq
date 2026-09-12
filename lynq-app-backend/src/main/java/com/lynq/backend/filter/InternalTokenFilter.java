package com.lynq.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.backend.controller.response.ErrorRestResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalTokenFilter extends OncePerRequestFilter {

  private static final String INTERNAL_TOKEN_HEADER = "lynq-internal-token";
  private static final String INVALID_INTERNAL_TOKEN_ERROR = "Invalid internal token";
  private static final String INTERNAL_PATH_PREFIX = "/internal";

  private final ObjectMapper objectMapper;
  private final String expectedToken;

  public InternalTokenFilter(ObjectMapper objectMapper, String expectedToken) {
    this.objectMapper = objectMapper;
    this.expectedToken = expectedToken;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().startsWith(INTERNAL_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (!matchesExpectedToken(request.getHeader(INTERNAL_TOKEN_HEADER))) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      ErrorRestResponse<Void> errorResponse =
          new ErrorRestResponse<>(null, INVALID_INTERNAL_TOKEN_ERROR);
      objectMapper.writeValue(response.getWriter(), errorResponse);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean matchesExpectedToken(String provided) {
    if (expectedToken == null || expectedToken.isBlank() || provided == null) {
      return false;
    }
    return MessageDigest.isEqual(
        provided.getBytes(StandardCharsets.UTF_8),
        expectedToken.getBytes(StandardCharsets.UTF_8));
  }
}
