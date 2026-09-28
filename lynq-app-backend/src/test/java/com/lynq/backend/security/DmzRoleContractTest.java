package com.lynq.backend.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

class DmzRoleContractTest {

  private static final String CONTROLLER_PACKAGE = "com.lynq.backend.controller";
  private static final String DMZ_PREFIX = "/dmz";

  private static final Set<String> OPEN_TO_EVERY_CALLER = Set.of(
      "GET /dmz/user",
      "POST /dmz/user",
      "PATCH /dmz/user",
      "GET /dmz/user/{userId}",
      "GET /dmz/user/generate-upload-image",
      "POST /dmz/user/confirm-upload-image",
      "GET /dmz/company/{companyId}",
      "GET /dmz/company/generate-upload-image",
      "POST /dmz/company/confirm-upload-image",
      "GET /dmz/job",
      "GET /dmz/job/{jobId}/details",
      "PATCH /dmz/job/{jobId}/increase-seen");

  private static final Set<String> AUTHORIZED_BY_OWNERSHIP = Set.of(
      "PATCH /dmz/company",
      "PATCH /dmz/job/{jobId}",
      "PATCH /dmz/job/{jobId}/refresh",
      "PATCH /dmz/job/{jobId}/close",
      "GET /dmz/job/{jobId}/candidates",
      "GET /dmz/job/{jobId}/candidate/{candidateId}/candidate-explanation");

  @Test
  @DisplayName("a /dmz endpoint either names a role or is declared as one that does not need one")
  void everyDmzEndpointNamesARoleOrIsDeclaredWithoutOne() {
    Set<String> undeclared = new TreeSet<>();

    for (Endpoint endpoint : dmzEndpoints()) {
      if (endpoint.roleChecked() || isDeclared(endpoint.route())) {
        continue;
      }
      undeclared.add(endpoint.route());
    }

    assertThat(
        "These /dmz endpoints enforce no role. Add @HasRole, or declare the route in "
            + "OPEN_TO_EVERY_CALLER or AUTHORIZED_BY_OWNERSHIP: " + undeclared,
        undeclared, is(empty()));
  }

  @Test
  @DisplayName("a declared exception that no longer maps to an endpoint is removed")
  void theDeclaredExceptionsAllStillExist() {
    Set<String> routes = new LinkedHashSet<>();
    dmzEndpoints().forEach(endpoint -> routes.add(endpoint.route()));

    Set<String> stale = new TreeSet<>();
    stale.addAll(OPEN_TO_EVERY_CALLER);
    stale.addAll(AUTHORIZED_BY_OWNERSHIP);
    stale.removeAll(routes);

    assertThat(
        "These routes are declared as needing no role but no longer exist: " + stale,
        stale, is(empty()));
  }

  private static boolean isDeclared(String route) {
    return OPEN_TO_EVERY_CALLER.contains(route) || AUTHORIZED_BY_OWNERSHIP.contains(route);
  }

  private static List<Endpoint> dmzEndpoints() {
    List<Endpoint> endpoints = new ArrayList<>();

    for (Class<?> controller : restControllers()) {
      RequestMapping typeMapping =
          AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
      String base = firstPath(typeMapping);
      if (!base.startsWith(DMZ_PREFIX)) {
        continue;
      }
      boolean roleOnType = AnnotatedElementUtils.hasAnnotation(controller, HasRole.class);

      for (Method method : controller.getDeclaredMethods()) {
        RequestMapping mapping =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
          continue;
        }
        boolean roleChecked =
            roleOnType || AnnotatedElementUtils.hasAnnotation(method, HasRole.class);

        for (String verb : verbs(mapping)) {
          for (String path : paths(mapping)) {
            endpoints.add(new Endpoint(verb + " " + join(base, path), roleChecked));
          }
        }
      }
    }
    return endpoints;
  }

  private static List<Class<?>> restControllers() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<Class<?>> controllers = new ArrayList<>();
    for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
      try {
        controllers.add(Class.forName(definition.getBeanClassName()));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Scanned a controller that cannot be loaded", e);
      }
    }
    return controllers;
  }

  private static String firstPath(RequestMapping mapping) {
    return mapping == null || mapping.path().length == 0 ? "" : mapping.path()[0];
  }

  private static List<String> paths(RequestMapping mapping) {
    return mapping.path().length == 0 ? List.of("") : List.of(mapping.path());
  }

  private static List<String> verbs(RequestMapping mapping) {
    if (mapping.method().length == 0) {
      return List.of("ANY");
    }
    List<String> verbs = new ArrayList<>();
    for (RequestMethod requestMethod : mapping.method()) {
      verbs.add(requestMethod.name().toUpperCase(Locale.ROOT));
    }
    return verbs;
  }

  private static String join(String base, String path) {
    if (path.isEmpty()) {
      return base;
    }
    return base + (path.startsWith("/") ? path : "/" + path);
  }

  private record Endpoint(String route, boolean roleChecked) {
  }
}
