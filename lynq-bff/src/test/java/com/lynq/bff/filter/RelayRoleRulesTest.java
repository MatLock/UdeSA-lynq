package com.lynq.bff.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.lynq.bff.security.Role;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RelayRoleRulesTest {

  @ParameterizedTest
  @CsvSource({
      "POST,/job",
      "GET,/job/mine",
      "POST,/company",
  })
  void companyOnlyRelayedRoutes(String method, String path) {
    assertThat(RelayRoleRules.requiredRole(method, path), is(Optional.of(Role.COMPANY)));
  }

  @ParameterizedTest
  @CsvSource({
      "POST,/job/018f9c3a/apply",
      "GET,/job/018f9c3a/upskilling-suggestion",
      "GET,/user/generate-upload-resume",
      "POST,/user/confirm-upload-resume",
      "GET,/user/resume",
      "GET,/user/resume/languages",
      "POST,/user/resume",
      "PUT,/user/resume/018f9c3a/alias",
      "DELETE,/user/resume/018f9c3a",
      "GET,/user/application",
      "GET,/user/upskilling-suggestion/018f9c3a",
  })
  void candidateOnlyRelayedRoutes(String method, String path) {
    assertThat(RelayRoleRules.requiredRole(method, path), is(Optional.of(Role.CANDIDATE)));
  }

  @ParameterizedTest
  @CsvSource({
      "GET,/user",
      "PATCH,/user",
      "POST,/user",
      "GET,/user/11111111",
      "GET,/job",
      "GET,/job/018f9c3a/details",
      "PATCH,/job/018f9c3a",
      "GET,/job/018f9c3a/candidates",
      "GET,/company/018f9c3a",
      "POST,/files/upload-url",
      "POST,/resume/preview",
  })
  void routesTheGatewayLetsThrough(String method, String path) {
    assertThat(RelayRoleRules.requiredRole(method, path), is(Optional.empty()));
  }

  @ParameterizedTest
  @CsvSource({
      "GET,/job",
      "DELETE,/job/mine",
      "GET,/company",
      "GET,/user/resume/018f9c3a",
  })
  void aRuleOnlyAppliesToItsOwnMethod(String method, String path) {
    assertThat(RelayRoleRules.requiredRole(method, path), is(Optional.empty()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void noRuleAppliesToABlankPath(String path) {
    assertThat(RelayRoleRules.requiredRole("GET", path), is(Optional.empty()));
  }

  @ParameterizedTest
  @CsvSource(value = {"null,/job", "POST,null"}, nullValues = "null")
  void noRuleAppliesWithoutAMethodOrAPath(String method, String path) {
    assertThat(RelayRoleRules.requiredRole(method, path), is(Optional.empty()));
  }
}
