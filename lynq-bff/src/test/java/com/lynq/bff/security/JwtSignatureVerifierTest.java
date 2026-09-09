package com.lynq.bff.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtSignatureVerifierTest {

  private static final String SECRET =
      "8d2b6d2a9c5e8f4f8f9d1c7a4e3b5f6d8c9a1e2f3a4b5c6d7e8f9a1b2c3d4e5";
  private static final String OTHER_SECRET =
      "0000000000000000000000000000000000000000000000000000000000000000";

  private static final String SUBJECT = "11111111-1111-1111-1111-111111111111";
  private static final List<String> ROLES = List.of("R_CANDIDATE");
  private static final String NOT_A_JWT = "definitely-not-a-jwt";
  private static final String BLANK_TOKEN = "   ";

  private JwtSignatureVerifier verifier;

  @BeforeEach
  void setUp() {
    verifier = new JwtSignatureVerifier(SECRET);
  }

  @Test
  void returnsTheSubjectOfATokenSignedWithTheSharedSecret() {
    String token = token(SECRET, SUBJECT, Instant.now().plus(15, ChronoUnit.MINUTES));

    assertThat(verifier.verify(token), is(Optional.of(new VerifiedCaller(SUBJECT, ROLES))));
  }

  @Test
  void returnsTheRolesTheTokenCarries() {
    String token = token(SECRET, SUBJECT, Instant.now().plus(15, ChronoUnit.MINUTES),
        List.of("R_COMPANY"));

    assertThat(verifier.verify(token).orElseThrow().roles(), is(List.of("R_COMPANY")));
  }

  @Test
  void returnsNoRolesForATokenMintedWithoutTheRolesClaim() {
    String token = token(SECRET, SUBJECT, Instant.now().plus(15, ChronoUnit.MINUTES), null);

    assertThat(verifier.verify(token).orElseThrow().roles(), is(List.of()));
  }

  @Test
  void rejectsATokenSignedWithAnotherSecret() {
    String token = token(OTHER_SECRET, SUBJECT, Instant.now().plus(15, ChronoUnit.MINUTES));

    assertThat(verifier.verify(token).isEmpty(), is(true));
  }

  @Test
  void rejectsAnExpiredToken() {
    String token = token(SECRET, SUBJECT, Instant.now().minus(1, ChronoUnit.MINUTES));

    assertThat(verifier.verify(token).isEmpty(), is(true));
  }

  @Test
  void rejectsATamperedPayload() {
    String token = token(SECRET, SUBJECT, Instant.now().plus(15, ChronoUnit.MINUTES));
    String[] parts = token.split("\\.");
    String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA."
        + parts[2];

    assertThat(verifier.verify(tampered).isEmpty(), is(true));
  }

  @Test
  void rejectsAValidlySignedTokenThatCarriesNoSubject() {
    String token = token(SECRET, null, Instant.now().plus(15, ChronoUnit.MINUTES));

    assertThat(verifier.verify(token).isEmpty(), is(true));
  }

  @Test
  void rejectsSomethingThatIsNotAJwtAtAll() {
    assertThat(verifier.verify(NOT_A_JWT).isEmpty(), is(true));
  }

  @Test
  void rejectsANullToken() {
    assertThat(verifier.verify(null).isEmpty(), is(true));
  }

  @Test
  void rejectsABlankToken() {
    assertThat(verifier.verify(BLANK_TOKEN).isEmpty(), is(true));
  }

  private static String token(String secret, String subject, Instant expiration) {
    return token(secret, subject, expiration, ROLES);
  }

  private static String token(String secret, String subject, Instant expiration,
      List<String> roles) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    var builder = Jwts.builder()
        .subject(subject)
        .issuedAt(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
        .expiration(Date.from(expiration));

    if (roles != null) {
      builder.claim("roles", roles);
    }

    return builder.signWith(key).compact();
  }
}
