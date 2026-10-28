package com.lynq.bff.filter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * What the gateway answers without a caller it has already authenticated.
 *
 * <p>Everything else is closed: the filters run on {@code /*}, so a route that is not named here
 * needs a bearer token whose signature checks out before it reaches a controller. The auth routes
 * relayed to lynq-iam are the exception, and they are listed as <b>exact paths</b> rather than an
 * {@code /auth} prefix on purpose — a prefix would open whatever lynq-iam grows next by accident,
 * which is the wrong default for the one door into the platform.
 */
final class PublicPaths {

  private static final String SWAGGER_UI_HTML = "/swagger-ui.html";
  private static final String[] WHITELISTED_PATH_PREFIXES = {
      "/swagger-ui",
      "/v3/api-docs",
      "/swagger-resources",
      "/webjars"
  };

  /**
   * The relayed auth routes that carry no token at all: the two logins and the registration take a
   * password in the body, and the availability checks run before an account exists. The refresh and
   * the logout carry no header either: their credential is the HttpOnly refresh cookie, an opaque,
   * Redis-backed token that is not a JWT and that only lynq-iam can check. There is nothing for this
   * service to verify, and lynq-iam checks the credentials itself.
   */
  private static final Set<String> ANONYMOUS_AUTH_PATHS = Set.of(
      "/auth/register",
      "/auth/login/username",
      "/auth/login/email",
      "/auth/refresh",
      "/auth/logout",
      "/auth/check-username",
      "/auth/check-email");

  private PublicPaths() {
  }

  /** True when the route needs no Authorization header whatsoever. */
  static boolean isPublic(HttpServletRequest request) {
    String path = request.getServletPath();
    if (SWAGGER_UI_HTML.equals(path) || ANONYMOUS_AUTH_PATHS.contains(path)) {
      return true;
    }
    for (String prefix : WHITELISTED_PATH_PREFIXES) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
