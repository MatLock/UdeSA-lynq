package com.lynq.bff.filter;

import jakarta.servlet.http.HttpServletRequest;

final class PublicPaths {

  private static final String SWAGGER_UI_HTML = "/swagger-ui.html";
  private static final String[] WHITELISTED_PATH_PREFIXES = {
      "/swagger-ui",
      "/v3/api-docs",
      "/swagger-resources",
      "/webjars"
  };

  private PublicPaths() {
  }

  static boolean isPublic(HttpServletRequest request) {
    String path = request.getServletPath();
    if (SWAGGER_UI_HTML.equals(path)) {
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
