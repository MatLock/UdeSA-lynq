package com.lynq.iam.service;

import com.lynq.iam.model.UserEntity;
import com.lynq.iam.repository.UserRepository;
import com.lynq.iam.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String SAMPLE_USERNAME = "johndoe";
  private static final String SAMPLE_PASSWORD = "P@ssw0rd123";
  private static final String SAMPLE_EMAIL = "johndoe@example.com";
  private static final String SAMPLE_ENCODED_PASSWORD = "$2a$10$encodedpasswordhash";
  private static final String SAMPLE_NEW_PASSWORD = "N3wStr0ngPass!";
  private static final String SAMPLE_NEW_ENCODED_PASSWORD = "$2a$10$newencodedpasswordhash";
  private static final String SAMPLE_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String EXISTING_USERNAME_REASON_FRAGMENT = "Username already exists";
  private static final String EXISTING_EMAIL_REASON_FRAGMENT = "Email already exists";
  private static final String USER_NOT_FOUND_REASON_FRAGMENT = "User not found";

  @Mock
  private UserRepository userRepository;

  @Mock
  private BCryptPasswordEncoder passwordEncoder;

  @Mock
  private UserEntity savedUserEntity;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, passwordEncoder);
  }

  @Test
  void createUserReturnsSavedEntityWhenUsernameAndEmailAreUnique() {
    when(userRepository.existsByUsername(SAMPLE_USERNAME)).thenReturn(false);
    when(userRepository.existsByEmail(SAMPLE_EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(SAMPLE_PASSWORD)).thenReturn(SAMPLE_ENCODED_PASSWORD);
    when(userRepository.save(any(UserEntity.class))).thenReturn(savedUserEntity);

    UserEntity result = userService.createUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL);

    assertThat(result, is(sameInstance(savedUserEntity)));
  }

  @Test
  void createUserPersistsEntityWithEncodedPasswordAndProvidedUsernameAndEmail() {
    when(userRepository.existsByUsername(SAMPLE_USERNAME)).thenReturn(false);
    when(userRepository.existsByEmail(SAMPLE_EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(SAMPLE_PASSWORD)).thenReturn(SAMPLE_ENCODED_PASSWORD);
    when(userRepository.save(any(UserEntity.class))).thenReturn(savedUserEntity);
    ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);

    userService.createUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL);

    verify(userRepository).save(entityCaptor.capture());
    UserEntity captured = entityCaptor.getValue();
    assertThat(captured.getUsername(), is(SAMPLE_USERNAME));
    assertThat(captured.getEmail(), is(SAMPLE_EMAIL));
    assertThat(captured.getPassword(), is(SAMPLE_ENCODED_PASSWORD));
    assertThat(captured.getId(), is(notNullValue()));
    assertThat(captured.getCreationDate(), is(notNullValue()));
  }

  @Test
  void createUserThrowsIllegalArgumentExceptionWhenUsernameAlreadyExists() {
    when(userRepository.existsByUsername(SAMPLE_USERNAME)).thenReturn(true);

    IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
        () -> userService.createUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL));

    assertThat(thrown.getMessage(), containsString(EXISTING_USERNAME_REASON_FRAGMENT));
    verify(userRepository, never()).save(any());
  }

  @Test
  void createUserThrowsIllegalArgumentExceptionWhenEmailAlreadyExists() {
    when(userRepository.existsByUsername(SAMPLE_USERNAME)).thenReturn(false);
    when(userRepository.existsByEmail(SAMPLE_EMAIL)).thenReturn(true);

    IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
        () -> userService.createUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL));

    assertThat(thrown.getMessage(), containsString(EXISTING_EMAIL_REASON_FRAGMENT));
    verify(userRepository, never()).save(any());
  }

  @Test
  void updatePasswordEncodesAndSavesUpdatedUserWhenUserExists() {
    UserEntity existingUser = UserEntity.builder().id(SAMPLE_USER_ID).build();
    when(userRepository.findById(SAMPLE_USER_ID)).thenReturn(Optional.of(existingUser));
    when(passwordEncoder.encode(SAMPLE_NEW_PASSWORD)).thenReturn(SAMPLE_NEW_ENCODED_PASSWORD);

    userService.updatePassword(SAMPLE_USER_ID, SAMPLE_NEW_PASSWORD);

    assertThat(existingUser.getPassword(), is(SAMPLE_NEW_ENCODED_PASSWORD));
    verify(userRepository).save(existingUser);
  }

  @Test
  void updatePasswordThrowsIllegalArgumentExceptionWhenUserNotFound() {
    when(userRepository.findById(SAMPLE_USER_ID)).thenReturn(Optional.empty());

    IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
        () -> userService.updatePassword(SAMPLE_USER_ID, SAMPLE_NEW_PASSWORD));

    assertThat(thrown.getMessage(), containsString(USER_NOT_FOUND_REASON_FRAGMENT));
    verify(userRepository, never()).save(any());
  }
}