package com.lynq.iam.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenGeneratorTest {

  private static final int RAW_TOKEN_BYTES = 64;
  private static final int EXPECTED_TOKEN_LENGTH = (int) Math.ceil(RAW_TOKEN_BYTES * 8.0 / 6.0);
  private static final Pattern URL_SAFE_BASE64_WITHOUT_PADDING = Pattern.compile("^[A-Za-z0-9_-]+$");
  private static final int UNIQUENESS_SAMPLE_SIZE = 1000;
  private static final int FIRST_ARGUMENT = 0;

  @Mock
  private SecureRandom secureRandom;

  private RefreshTokenGenerator refreshTokenGenerator;

  @BeforeEach
  void setUp() {
    AtomicInteger invocationCounter = new AtomicInteger();
    doAnswer(invocation -> {
      byte[] bytes = invocation.getArgument(FIRST_ARGUMENT);
      ByteBuffer.wrap(bytes).putInt(invocationCounter.incrementAndGet());
      return null;
    }).when(secureRandom).nextBytes(any(byte[].class));

    refreshTokenGenerator = new RefreshTokenGenerator(secureRandom);
  }

  @Test
  void generateReturnsNonNullNonBlankToken() {
    String token = refreshTokenGenerator.generate();

    assertThat(token, is(notNullValue()));
    assertThat(token.isBlank(), is(false));
  }

  @Test
  void generateReturnsTokenWithLengthMatchingBase64UrlWithoutPaddingOfRawBytes() {
    String token = refreshTokenGenerator.generate();

    assertThat(token.length(), is(EXPECTED_TOKEN_LENGTH));
  }

  @Test
  void generateReturnsTokenContainingOnlyUrlSafeBase64CharactersWithoutPadding() {
    String token = refreshTokenGenerator.generate();

    assertThat(URL_SAFE_BASE64_WITHOUT_PADDING.matcher(token).matches(), is(true));
  }

  @Test
  void generateRequestsRandomBytesFromTheInjectedSecureRandom() {
    refreshTokenGenerator.generate();

    verify(secureRandom).nextBytes(any(byte[].class));
  }

  @Test
  void generateReturnsDifferentTokenOnConsecutiveInvocations() {
    String firstToken = refreshTokenGenerator.generate();
    String secondToken = refreshTokenGenerator.generate();

    assertThat(firstToken, is(not(secondToken)));
  }

  @Test
  void generateReturnsUniqueTokensAcrossManyInvocations() {
    Set<String> tokens = new HashSet<>();

    for (int i = 0; i < UNIQUENESS_SAMPLE_SIZE; i++) {
      tokens.add(refreshTokenGenerator.generate());
    }

    assertThat(tokens.size(), is(UNIQUENESS_SAMPLE_SIZE));
  }
}
