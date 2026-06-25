package com.lynq.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class AppConfigTest {

  private static final String JAVA_TIME_MODULE_ID = "jackson-datatype-jsr310";
  private static final int SAMPLE_YEAR = 2025;
  private static final Month SAMPLE_MONTH = Month.JANUARY;
  private static final int SAMPLE_DAY = 15;
  private static final int SAMPLE_HOUR = 10;
  private static final int SAMPLE_MINUTE = 30;
  private static final LocalDateTime SAMPLE_DATETIME =
      LocalDateTime.of(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY, SAMPLE_HOUR, SAMPLE_MINUTE);
  private static final String EXPECTED_SERIALIZED_YEAR_FRAGMENT = String.valueOf(SAMPLE_YEAR);

  private AppConfig appConfig;

  @BeforeEach
  void setUp() {
    appConfig = new AppConfig();
  }

  @Test
  void createObjectMapperReturnsNonNullObjectMapperInstance() {
    ObjectMapper objectMapper = appConfig.createObjectMapper();

    assertThat(objectMapper, is(notNullValue()));
    assertThat(objectMapper, is(instanceOf(ObjectMapper.class)));
  }

  @Test
  void createObjectMapperRegistersJavaTimeModule() {
    ObjectMapper objectMapper = appConfig.createObjectMapper();

    assertThat(objectMapper.getRegisteredModuleIds(), hasItem(JAVA_TIME_MODULE_ID));
  }

  @Test
  void createObjectMapperSerializesJavaTimeTypes() throws Exception {
    ObjectMapper objectMapper = appConfig.createObjectMapper();

    String serialized = objectMapper.writeValueAsString(SAMPLE_DATETIME);

    assertThat(serialized, containsString(EXPECTED_SERIALIZED_YEAR_FRAGMENT));
  }
}