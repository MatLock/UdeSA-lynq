package com.lynq.bff.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  private static final String JAVA_TIME_MODULE_ID = "jackson-datatype-jsr310";
  private static final LocalDate SAMPLE_DATE = LocalDate.of(1995, Month.APRIL, 12);
  private static final String EXPECTED_SERIALIZED_YEAR_FRAGMENT = "1995";

  private AppConfig appConfig;

  @BeforeEach
  void setUp() {
    appConfig = new AppConfig();
  }

  @Test
  void createObjectMapperReturnsNonNullObjectMapperInstance() {
    assertThat(appConfig.createObjectMapper(), is(notNullValue()));
  }

  @Test
  void createObjectMapperRegistersJavaTimeModule() {
    ObjectMapper objectMapper = appConfig.createObjectMapper();

    assertThat(objectMapper.getRegisteredModuleIds(), hasItem(JAVA_TIME_MODULE_ID));
  }

  @Test
  void createObjectMapperSerializesJavaTimeTypes() throws Exception {
    ObjectMapper objectMapper = appConfig.createObjectMapper();

    assertThat(objectMapper.writeValueAsString(SAMPLE_DATE),
        containsString(EXPECTED_SERIALIZED_YEAR_FRAGMENT));
  }
}
