package com.lynq.bff.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.controller.response.ErrorRestResponse;
import com.lynq.bff.security.JwtSignatureVerifier;
import com.lynq.bff.security.VerifiedCaller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtSignatureFilter extends OncePerRequestFilter {

  public static final String VERIFIED_USER_ID = "com.lynq.bff.verifiedUserId";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String INVALID_TOKEN_ERROR = "Invalid or expired access token";

  private final JwtSignatureVerifier jwtSignatureVerifier;
  private final ObjectMapper objectMapper;

  public JwtSignatureFilter(JwtSignatureVerifier jwtSignatureVerifier, ObjectMapper objectMapper) {
    this.jwtSignatureVerifier = jwtSignatureVerifier;
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
    Optional<VerifiedCaller> verifiedCaller =
        jwtSignatureVerifier.verify(stripBearerPrefix(authHeader));

    if (verifiedCaller.isEmpty()) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      ErrorRestResponse<Void> errorResponse = new ErrorRestResponse<>(null, INVALID_TOKEN_ERROR);
      objectMapper.writeValue(response.getWriter(), errorResponse);
      return;
    }

    VerifiedCaller caller = verifiedCaller.get();
    request.setAttribute(VERIFIED_USER_ID, caller.userId());
    loadSecurityContext(caller, request);

    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void loadSecurityContext(VerifiedCaller caller, HttpServletRequest request) {
    List<GrantedAuthority> authorities = toAuthorities(caller.roles());
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(caller.userId(), null, authorities);
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static List<GrantedAuthority> toAuthorities(List<String> roles) {
    return roles == null ? List.of() : roles.stream()
        .filter(Objects::nonNull)
        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
        .toList();
  }

  private String stripBearerPrefix(String authHeader) {
    if (authHeader == null) {
      return null;
    }
    return authHeader.startsWith(BEARER_PREFIX)
        ? authHeader.substring(BEARER_PREFIX.length())
        : authHeader;
  }
}
