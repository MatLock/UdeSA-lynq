package com.lynq.backend.model;

import com.lynq.backend.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@ExtendWith(MockitoExtension.class)
class UserResumeEntityTest {

  private static final String USER_RESUME_ID = "77777777-7777-7777-7777-777777777777";
  private static final String RESUME = "{\"summary\":\"Backend engineer\"}";
  private static final Language LANGUAGE = Language.EN;
  private static final LocalDate CREATED_ON = LocalDate.of(2026, Month.JUNE, 25);
  private static final String LYNQ_FILE_STORAGE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";

  @Mock
  private UserEntity user;

  private UserResumeEntity userResumeEntity;

  @BeforeEach
  void setUp() {
    userResumeEntity = UserResumeEntity.builder()
        .id(USER_RESUME_ID)
        .resume(RESUME)
        .language(LANGUAGE)
        .createdOn(CREATED_ON)
        .lynqFileStorageId(LYNQ_FILE_STORAGE_ID)
        .user(user)
        .build();
  }

  @Test
  void builderPopulatesAllScalarFields() {
    assertThat(userResumeEntity.getId(), is(USER_RESUME_ID));
    assertThat(userResumeEntity.getResume(), is(RESUME));
    assertThat(userResumeEntity.getLanguage(), is(LANGUAGE));
    assertThat(userResumeEntity.getCreatedOn(), is(CREATED_ON));
    assertThat(userResumeEntity.getLynqFileStorageId(), is(LYNQ_FILE_STORAGE_ID));
  }

  @Test
  void builderWiresAssociatedUser() {
    assertThat(userResumeEntity.getUser(), is(sameInstance(user)));
  }

  @Test
  void settersUpdateFields() {
    UserResumeEntity target = new UserResumeEntity();

    target.setId(USER_RESUME_ID);
    target.setResume(RESUME);
    target.setLanguage(LANGUAGE);
    target.setCreatedOn(CREATED_ON);
    target.setLynqFileStorageId(LYNQ_FILE_STORAGE_ID);
    target.setUser(user);

    assertThat(target.getId(), is(USER_RESUME_ID));
    assertThat(target.getResume(), is(RESUME));
    assertThat(target.getLanguage(), is(LANGUAGE));
    assertThat(target.getCreatedOn(), is(CREATED_ON));
    assertThat(target.getLynqFileStorageId(), is(LYNQ_FILE_STORAGE_ID));
    assertThat(target.getUser(), is(sameInstance(user)));
  }
}