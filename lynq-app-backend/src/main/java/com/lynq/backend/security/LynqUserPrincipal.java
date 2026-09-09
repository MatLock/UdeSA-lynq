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

  public boolean hasRole(String role) {
    return authorities != null && authorities.stream()
        .anyMatch(authority -> (Role.PREFIX + role).equals(authority.getAuthority()));
  }
}
