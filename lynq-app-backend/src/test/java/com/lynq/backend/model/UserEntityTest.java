package com.lynq.backend.model;

import com.lynq.backend.enums.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith(MockitoExtension.class)
class UserEntityTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String FULL_NAME = "Ada Lovelace";
  private static final UserType USER_TYPE = UserType.CANDIDATE;
  private static final String LYNQ_FILE_STORAGE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String CURRENT_POSITION = "Senior Software Engineer";
  private static final String ABOUT = "Backend developer focused on distributed systems.";
  private static final String GITHUB_URL = "https://github.com/lynq-user";
  private static final String LINKEDIN_URL = "https://linkedin.com/in/lynq-user";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1995, Month.APRIL, 12);
  private static final LocalDate CREATED_ON = LocalDate.of(2026, Month.JUNE, 25);

  @Mock
  private UserSkillsEntity userSkill;

  private UserEntity userEntity;

  @BeforeEach
  void setUp() {
    userEntity = UserEntity.builder()
        .id(USER_ID)
        .fullName(FULL_NAME)
        .type(USER_TYPE)
        .lynqFileStorageId(LYNQ_FILE_STORAGE_ID)
        .currentPosition(CURRENT_POSITION)
        .about(ABOUT)
        .githubUrl(GITHUB_URL)
        .linkedinUrl(LINKEDIN_URL)
        .birthDate(BIRTH_DATE)
        .createdOn(CREATED_ON)
        .build();
  }

  @Test
  void builderPopulatesAllScalarFields() {
    assertThat(userEntity.getId(), is(USER_ID));
    assertThat(userEntity.getFullName(), is(FULL_NAME));
    assertThat(userEntity.getType(), is(USER_TYPE));
    assertThat(userEntity.getLynqFileStorageId(), is(LYNQ_FILE_STORAGE_ID));
    assertThat(userEntity.getCurrentPosition(), is(CURRENT_POSITION));
    assertThat(userEntity.getAbout(), is(ABOUT));
    assertThat(userEntity.getGithubUrl(), is(GITHUB_URL));
    assertThat(userEntity.getLinkedinUrl(), is(LINKEDIN_URL));
    assertThat(userEntity.getBirthDate(), is(BIRTH_DATE));
    assertThat(userEntity.getCreatedOn(), is(CREATED_ON));
  }

  @Test
  void skillsCollectionDefaultsToEmptyAndIsNotNull() {
    assertThat(userEntity.getSkills(), is(notNullValue()));
    assertThat(userEntity.getSkills(), is(empty()));
  }

  @Test
  void settersUpdateScalarFields() {
    UserEntity target = new UserEntity();

    target.setId(USER_ID);
    target.setType(USER_TYPE);
    target.setCreatedOn(CREATED_ON);

    assertThat(target.getId(), is(USER_ID));
    assertThat(target.getType(), is(USER_TYPE));
    assertThat(target.getCreatedOn(), is(CREATED_ON));
  }

  @Test
  void skillsAssociationHoldsAssignedSkill() {
    userEntity.setSkills(List.of(userSkill));

    assertThat(userEntity.getSkills(), contains(userSkill));
  }
}