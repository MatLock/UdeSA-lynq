package com.lynq.iam.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

  private static final String SAMPLE_RAW_PASSWORD = "S3cret!Passw0rd";
  private static final String DIFFERENT_RAW_PASSWORD = "another-password";

  @Mock(answer = Answers.RETURNS_SELF)
  private HttpSecurity httpSecurity;

  @Mock
  private DefaultSecurityFilterChain mockSecurityFilterChain;

  private SecurityConfig securityConfig;

  @BeforeEach
  void setUp() {
    securityConfig = new SecurityConfig();
  }

  @Test
  void passwordEncoderReturnsNonNullBCryptPasswordEncoder() {
    BCryptPasswordEncoder encoder = securityConfig.passwordEncoder();

    assertThat(encoder, is(notNullValue()));
    assertThat(encoder, is(instanceOf(BCryptPasswordEncoder.class)));
  }

  @Test
  void passwordEncoderEncodesPasswordIntoNonNullNonRawHash() {
    BCryptPasswordEncoder encoder = securityConfig.passwordEncoder();

    String encoded = encoder.encode(SAMPLE_RAW_PASSWORD);

    assertThat(encoded, is(notNullValue()));
    assertThat(encoded.equals(SAMPLE_RAW_PASSWORD), is(false));
  }

  @Test
  void passwordEncoderMatchesEncodedPasswordWithOriginalRawPassword() {
    BCryptPasswordEncoder encoder = securityConfig.passwordEncoder();

    String encoded = encoder.encode(SAMPLE_RAW_PASSWORD);

    assertThat(encoder.matches(SAMPLE_RAW_PASSWORD, encoded), is(true));
  }

  @Test
  void passwordEncoderDoesNotMatchEncodedPasswordWithDifferentRawPassword() {
    BCryptPasswordEncoder encoder = securityConfig.passwordEncoder();

    String encoded = encoder.encode(SAMPLE_RAW_PASSWORD);

    assertThat(encoder.matches(DIFFERENT_RAW_PASSWORD, encoded), is(false));
  }

  @Test
  void securityFilterChainReturnsChainBuiltFromHttpSecurity() throws Exception {
    when(httpSecurity.build()).thenReturn(mockSecurityFilterChain);

    SecurityFilterChain result = securityConfig.securityFilterChain(httpSecurity);

    assertThat(result, is(notNullValue()));
    assertThat(result, is(sameInstance(mockSecurityFilterChain)));
  }

  @Test
  void securityFilterChainDisablesCsrfFormLoginAndHttpBasic() throws Exception {
    when(httpSecurity.build()).thenReturn(mockSecurityFilterChain);

    securityConfig.securityFilterChain(httpSecurity);

    verify(httpSecurity).csrf(any());
    verify(httpSecurity).formLogin(any());
    verify(httpSecurity).httpBasic(any());
  }

  @Test
  void securityFilterChainConfiguresSessionManagementAndAuthorizationRules() throws Exception {
    when(httpSecurity.build()).thenReturn(mockSecurityFilterChain);

    securityConfig.securityFilterChain(httpSecurity);

    verify(httpSecurity).sessionManagement(any());
    verify(httpSecurity).authorizeHttpRequests(any());
  }

  @Test
  void securityFilterChainInvokesBuildOnHttpSecurity() throws Exception {
    when(httpSecurity.build()).thenReturn(mockSecurityFilterChain);

    securityConfig.securityFilterChain(httpSecurity);

    verify(httpSecurity).build();
  }
}