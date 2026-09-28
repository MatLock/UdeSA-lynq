package com.lynq.backend.security;

import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class LynqUserPrincipal {

  private final String id;
  private final String username;
  private final String email;
  private final Collection<GrantedAuthority> authorities;

  /**
   * The Authorization header this request arrived with, verbatim.
   *
   * <p>It is carried so a downstream service can resolve the same caller against lynq-iam for
   * itself: this service relays the credential it was given rather than asserting an identity by
   * user id. It is excluded from {@code toString} and {@code equals} on purpose — a bearer token has
   * no place in a log line, and the identity of a principal is the user, not the credential.
   */
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private final String authorization;

  public boolean hasRole(String role) {
    return authorities != null && authorities.stream()
        .anyMatch(authority -> (Role.PREFIX + role).equals(authority.getAuthority()));
  }
}
