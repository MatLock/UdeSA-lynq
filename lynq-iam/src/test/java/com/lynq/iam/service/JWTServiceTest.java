package com.lynq.iam.service;

import com.lynq.iam.model.UserEntity;
import com.lynq.iam.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

class JWTServiceTest {

  private static final String SECRET = "this-is-a-very-secret-key-for-hmac-sha256-tests";
  private static final String DIFFERENT_SECRET = "a-completely-different-secret-key-for-tampering";
  private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15L;
  private static final String SAMPLE_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String SAMPLE_USERNAME = "johndoe";
  private static final String SAMPLE_EMAIL = "johndoe@example.com";
  private static final String OTHER_USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER_USERNAME = "janedoe";
  private static final String OTHER_EMAIL = "janedoe@example.com";
  private static final String MALFORMED_TOKEN = "not.a.valid.token";
  private static final boolean EXPECTED_VALID = true;
  private static final boolean EXPECTED_INVALID = false;

  private JWTService jwtService;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    jwtService = new JWTService(SECRET, ACCESS_TOKEN_EXPIRATION_MINUTES);
    user = UserEntity.builder()
        .id(SAMPLE_USER_ID)
        .username(SAMPLE_USERNAME)
        .email(SAMPLE_EMAIL)
        .build();
  }

  @Test
  void generateAccessTokenReturnsNonNullNonBlankToken() {
    String token = jwtService.generateAccessToken(user);

    assertThat(token, is(notNullValue()));
    assertThat(token.isBlank(), is(false));
  }

  @Test
  void generateAccessTokenProducesTokenFromWhichSubjectMatchesUserId() {
    String token = jwtService.generateAccessToken(user);

    assertThat(jwtService.extractUserId(token), is(SAMPLE_USER_ID));
  }

  @Test
  void generateAccessTokenProducesTokenFromWhichUsernameClaimMatchesUser() {
    String token = jwtService.generateAccessToken(user);

    assertThat(jwtService.extractUsername(token), is(SAMPLE_USERNAME));
  }

  @Test
  void generateAccessTokenProducesTokenFromWhichEmailClaimMatchesUser() {
    String token = jwtService.generateAccessToken(user);

    assertThat(jwtService.extractEmail(token), is(SAMPLE_EMAIL));
  }

  @Test
  void generateAccessTokenProducesDifferentTokensForDifferentUsers() {
    UserEntity otherUser = UserEntity.builder()
        .id(OTHER_USER_ID)
        .username(OTHER_USERNAME)
        .email(OTHER_EMAIL)
        .build();

    String firstToken = jwtService.generateAccessToken(user);
    String secondToken = jwtService.generateAccessToken(otherUser);

    assertThat(firstToken, is(not(secondToken)));
  }

  @Test
  void isAccessTokenValidReturnsTrueForFreshlyGeneratedToken() {
    String token = jwtService.generateAccessToken(user);

    assertThat(jwtService.isAccessTokenValid(token), is(EXPECTED_VALID));
  }

  @Test
  void isAccessTokenValidReturnsFalseForMalformedToken() {
    assertThat(jwtService.isAccessTokenValid(MALFORMED_TOKEN), is(EXPECTED_INVALID));
  }

  @Test
  void isAccessTokenValidReturnsFalseForTokenSignedWithDifferentSecret() {
    JWTService otherService = new JWTService(DIFFERENT_SECRET, ACCESS_TOKEN_EXPIRATION_MINUTES);
    String foreignToken = otherService.generateAccessToken(user);

    assertThat(jwtService.isAccessTokenValid(foreignToken), is(EXPECTED_INVALID));
  }
}