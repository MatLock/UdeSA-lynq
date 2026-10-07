package com.lynq.bff.ratelimit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.lynq.bff.controller.impl.JobControllerImpl;
import com.lynq.bff.controller.impl.LlmControllerImpl;
import com.lynq.bff.controller.impl.ResumeControllerImpl;
import com.lynq.bff.controller.impl.UserControllerImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RateLimitedEndpointsTest {

  static List<Arguments> endpointsReachingLlmOrAgent() {
    return List.of(
        Arguments.of(LlmControllerImpl.class, "detectLanguage", RateLimitTier.LIGHT),
        Arguments.of(LlmControllerImpl.class, "enhanceSkills", RateLimitTier.STANDARD),
        Arguments.of(LlmControllerImpl.class, "extractResumeSkills", RateLimitTier.STANDARD),
        Arguments.of(LlmControllerImpl.class, "translateResume", RateLimitTier.HEAVY),
        Arguments.of(ResumeControllerImpl.class, "getResumeTailoringConversation",
            RateLimitTier.LIGHT),
        Arguments.of(ResumeControllerImpl.class, "previewResume", RateLimitTier.STANDARD),
        Arguments.of(ResumeControllerImpl.class, "applyWithTailoredResume",
            RateLimitTier.STANDARD),
        Arguments.of(ResumeControllerImpl.class, "importResumeDocument", RateLimitTier.HEAVY),
        Arguments.of(ResumeControllerImpl.class, "translateResume", RateLimitTier.HEAVY),
        Arguments.of(ResumeControllerImpl.class, "startResumeTailoring", RateLimitTier.HEAVY),
        Arguments.of(ResumeControllerImpl.class, "takeResumeTailoringTurn", RateLimitTier.HEAVY),
        Arguments.of(JobControllerImpl.class, "explainCandidate", RateLimitTier.STANDARD),
        Arguments.of(JobControllerImpl.class, "suggestUpskilling", RateLimitTier.STANDARD),
        Arguments.of(UserControllerImpl.class, "suggestUpskilling", RateLimitTier.STANDARD));
  }

  @ParameterizedTest(name = "{0}#{1} is {2}")
  @MethodSource("endpointsReachingLlmOrAgent")
  void everyEndpointReachingLlmOrAgentIsRateLimited(Class<?> controller, String methodName,
                                                    RateLimitTier tier) {
    RateLimited rateLimited = methodNamed(controller, methodName).getAnnotation(RateLimited.class);

    assertThat(rateLimited, is(notNullValue()));
    assertThat(rateLimited.value(), is(tier));
  }

  private static Method methodNamed(Class<?> controller, String methodName) {
    return Arrays.stream(controller.getDeclaredMethods())
        .filter(method -> method.getName().equals(methodName))
        .findFirst()
        .orElseThrow();
  }
}
