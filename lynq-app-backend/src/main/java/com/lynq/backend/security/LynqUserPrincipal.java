package com.lynq.backend.security;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class LynqUserPrincipal {

  private final String id;
  private final String username;
  private final String email;

}
