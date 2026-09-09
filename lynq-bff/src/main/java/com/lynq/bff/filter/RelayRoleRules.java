package com.lynq.bff.filter;

import com.lynq.bff.security.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

final class RelayRoleRules {

  private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

  private static final List<Rule> RULES = List.of(
      rule("POST", "/job", Role.COMPANY),
      rule("GET", "/job/mine", Role.COMPANY),
      rule("POST", "/company", Role.COMPANY),
      rule("POST", "/job/{jobId}/apply", Role.CANDIDATE),
      rule("GET", "/job/{jobId}/upskilling-suggestion", Role.CANDIDATE),
      rule("GET", "/user/generate-upload-resume", Role.CANDIDATE),
      rule("POST", "/user/confirm-upload-resume", Role.CANDIDATE),
      rule("GET", "/user/resume", Role.CANDIDATE),
      rule("GET", "/user/resume/languages", Role.CANDIDATE),
      rule("POST", "/user/resume", Role.CANDIDATE),
      rule("PUT", "/user/resume/{resumeId}/alias", Role.CANDIDATE),
      rule("DELETE", "/user/resume/{resumeId}", Role.CANDIDATE),
      rule("GET", "/user/application", Role.CANDIDATE),
      rule("GET", "/user/upskilling-suggestion/{jobPostId}", Role.CANDIDATE));

  private RelayRoleRules() {
  }

  static Optional<String> requiredRole(String method, String path) {
    if (method == null || path == null || path.isBlank()) {
      return Optional.empty();
    }

    PathContainer requestPath = PathContainer.parsePath(path);
    return RULES.stream()
        .filter(rule -> rule.matches(method, requestPath))
        .map(Rule::role)
        .findFirst();
  }

  private static Rule rule(String method, String pattern, String role) {
    return new Rule(method, PARSER.parse(pattern), role);
  }

  private record Rule(String method, PathPattern path, String role) {

    boolean matches(String requestMethod, PathContainer requestPath) {
      return method.equalsIgnoreCase(requestMethod) && path.matches(requestPath);
    }
  }
}
