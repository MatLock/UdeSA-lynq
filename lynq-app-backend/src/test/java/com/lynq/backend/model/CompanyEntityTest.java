package com.lynq.backend.model;

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
class CompanyEntityTest {

  private static final String COMPANY_ID = "22222222-2222-2222-2222-222222222222";
  private static final String COMPANY_NAME = "Lynq Technologies";
  private static final String ABOUT = "We build talent matching platforms.";
  private static final Integer COMPANY_SIZE = 250;
  private static final String LYNQ_FILE_STORAGE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final LocalDate CREATED_ON = LocalDate.of(2026, Month.JUNE, 25);

  @Mock
  private JobPostEntity jobPost;

  private CompanyEntity companyEntity;

  @BeforeEach
  void setUp() {
    companyEntity = CompanyEntity.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .about(ABOUT)
        .size(COMPANY_SIZE)
        .lynqFileStorageId(LYNQ_FILE_STORAGE_ID)
        .createdOn(CREATED_ON)
        .build();
  }

  @Test
  void builderPopulatesAllScalarFields() {
    assertThat(companyEntity.getId(), is(COMPANY_ID));
    assertThat(companyEntity.getName(), is(COMPANY_NAME));
    assertThat(companyEntity.getAbout(), is(ABOUT));
    assertThat(companyEntity.getSize(), is(COMPANY_SIZE));
    assertThat(companyEntity.getLynqFileStorageId(), is(LYNQ_FILE_STORAGE_ID));
    assertThat(companyEntity.getCreatedOn(), is(CREATED_ON));
  }

  @Test
  void jobPostsCollectionDefaultsToEmptyAndIsNotNull() {
    assertThat(companyEntity.getJobPosts(), is(notNullValue()));
    assertThat(companyEntity.getJobPosts(), is(empty()));
  }

  @Test
  void settersUpdateScalarFields() {
    CompanyEntity target = new CompanyEntity();

    target.setId(COMPANY_ID);
    target.setName(COMPANY_NAME);
    target.setSize(COMPANY_SIZE);
    target.setCreatedOn(CREATED_ON);

    assertThat(target.getId(), is(COMPANY_ID));
    assertThat(target.getName(), is(COMPANY_NAME));
    assertThat(target.getSize(), is(COMPANY_SIZE));
    assertThat(target.getCreatedOn(), is(CREATED_ON));
  }

  @Test
  void jobPostsAssociationHoldsAssignedJobPost() {
    companyEntity.setJobPosts(List.of(jobPost));

    assertThat(companyEntity.getJobPosts(), contains(jobPost));
  }
}