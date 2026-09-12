package com.lynq.bff.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.controller.response.ErrorRestResponse;
import com.lynq.bff.security.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Log4j2
public class RelayRoleFilter extends OncePerRequestFilter {

  private static final String ONLY_ROLE_CAN_PERFORM =
      "Only users of type %s can perform this action";

  private final ObjectMapper objectMapper;

  public RelayRoleFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // The role table only covers relayed routes, and reading it needs the security context the
    // signature check loads: a route without one has nothing to bounce early.
    return PublicPaths.isSignatureExempt(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    Optional<String> requiredRole =
        RelayRoleRules.requiredRole(request.getMethod(), request.getServletPath());

    if (requiredRole.isPresent() && !holdsRole(requiredRole.get())) {
      log.warn("message= Relayed route not reachable with the caller's roles, method={}, path={}, "
          + "required_role={}", request.getMethod(), request.getServletPath(), requiredRole.get());
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getWriter(),
          new ErrorRestResponse<Void>(null, String.format(ONLY_ROLE_CAN_PERFORM, requiredRole.get())));
      return;
    }

    filterChain.doFilter(request, response);
  }

  private static boolean holdsRole(String role) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getAuthorities().stream()
        .anyMatch(authority -> (Role.PREFIX + role).equals(authority.getAuthority()));
  }
}
