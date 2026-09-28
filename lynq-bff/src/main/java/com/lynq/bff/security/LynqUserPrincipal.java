package com.lynq.bff.security;

import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;

/**
 * The caller of a gateway route, as the access token describes them.
 *
 * <p>It mirrors the principal lynq-app-backend builds, so a route reads the caller the same way on
 * either side of the gateway. The difference is where it comes from: this service verifies the
 * token's signature itself rather than asking lynq-iam, because it is the front door and every
 * request would otherwise cost an extra hop before it is relayed anywhere.
 */
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
   * <p>Relayed services resolve the caller from this credential themselves, so the gateway carries
   * it rather than asserting an identity by user id. Excluded from {@code toString} and
   * {@code equals}: a bearer token has no place in a log line, and a principal's identity is the
   * user, not the credential.
   */
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private final String authorization;

  public boolean hasRole(String role) {
    return authorities != null && authorities.stream()
        .anyMatch(authority -> (Role.PREFIX + role).equals(authority.getAuthority()));
  }
}
