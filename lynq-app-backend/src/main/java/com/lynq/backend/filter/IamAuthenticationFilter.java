package com.lynq.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.backend.client.LynqIamClient;
import com.lynq.backend.client.response.UserInfoResponse;
import com.lynq.backend.controller.response.ErrorRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.security.LynqUserPrincipal;
import feign.FeignException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public class IamAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private static final String INVALID_TOKEN_ERROR = "Invalid or expired access token";
  private static final String IAM_UNAVAILABLE_ERROR = "Authentication service is unavailable";

  private final LynqIamClient lynqIamClient;
  private final ObjectMapper objectMapper;

  public IamAuthenticationFilter(LynqIamClient lynqIamClient, ObjectMapper objectMapper) {
    this.lynqIamClient = lynqIamClient;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return PublicPaths.isPublic(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

    String authHeader = request.getHeader(AUTHORIZATION_HEADER);
    String requestUuid = request.getHeader(REQUEST_UUID_HEADER);

    try {
      GlobalRestResponse<UserInfoResponse> userInfo = lynqIamClient.getUserInfo(authHeader, requestUuid);
      if (userInfo == null || userInfo.getData() == null) {
        writeError(response, HttpStatus.UNAUTHORIZED, INVALID_TOKEN_ERROR);
        return;
      }

      loadSecurityContext(userInfo.getData(), request);
    } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
      writeError(response, HttpStatus.UNAUTHORIZED, INVALID_TOKEN_ERROR);
      return;
    } catch (FeignException e) {
      writeError(response, HttpStatus.SERVICE_UNAVAILABLE, IAM_UNAVAILABLE_ERROR);
      return;
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void loadSecurityContext(UserInfoResponse userInfo, HttpServletRequest request) {
    LynqUserPrincipal principal = new LynqUserPrincipal(
        userInfo.getId(), userInfo.getUsername(), userInfo.getEmail());
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private void writeError(HttpServletResponse response, HttpStatus status, String reason) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ErrorRestResponse<Void> errorResponse = new ErrorRestResponse<>(null, reason);
    objectMapper.writeValue(response.getWriter(), errorResponse);
  }
}
