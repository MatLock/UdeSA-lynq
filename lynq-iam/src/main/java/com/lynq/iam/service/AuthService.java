package com.lynq.iam.service;

import com.lynq.iam.aspect.AuditLog;
import com.lynq.iam.controller.response.AccessTokenRefreshedResponse;
import com.lynq.iam.controller.response.UserInfoRestResponse;
import com.lynq.iam.controller.response.UserRestResponse;
import com.lynq.iam.exceptions.ForbiddenException;
import com.lynq.iam.exceptions.InvalidPasswordException;
import com.lynq.iam.exceptions.UserNotFoundException;
import com.lynq.iam.model.UserEntity;
import com.lynq.iam.repository.UserRepository;
import com.lynq.iam.security.RefreshTokenGenerator;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class AuthService {

  private static final String INVALID_REFRESH_TOKEN_EXCEPTION = "Invalid refresh token";
  private static final String USER_NOT_FOUND_EXCEPTION = "User not found";

  private final UserService userService;
  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final JWTService jwtService;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final RedisService redisService;

  public AuthService(UserService userService, UserRepository userRepository,
                     BCryptPasswordEncoder passwordEncoder, JWTService jwtService,
                     RefreshTokenGenerator refreshTokenGenerator, RedisService redisService) {
    this.userService = userService;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenGenerator = refreshTokenGenerator;
    this.redisService = redisService;
  }

  @AuditLog
  public UserRestResponse registerUser(String username, String password, String email) {
    UserEntity user = userService.createUser(username, password, email);

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = refreshTokenGenerator.generate();
    redisService.saveRefreshTokenForUser(user.getId(), refreshToken);

    return UserRestResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .build();
  }

  @AuditLog
  public UserRestResponse loginByUsername(String username, String password) {
    UserEntity user = userRepository.findByUsername(username)
        .orElseThrow(() -> {
          log.error("message= Username not found, username={}", username);
          return new UserNotFoundException("Invalid username or password");
        });

    if (!passwordEncoder.matches(password, user.getPassword())) {
      log.error("message= Invalid password for username, username={}", username);
      throw new InvalidPasswordException("Invalid username or password");
    }

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = refreshTokenGenerator.generate();
    redisService.saveRefreshTokenForUser(user.getId(), refreshToken);

    return UserRestResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .build();
  }

  @AuditLog
  public UserRestResponse loginByEmail(String email, String password) {
    UserEntity user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.error("message= Email not found, email={}", email);
          return new UserNotFoundException("Invalid email or password");
        });

    if (!passwordEncoder.matches(password, user.getPassword())) {
      log.error("message= Invalid password for email, email={}", email);
      throw new InvalidPasswordException("Invalid email or password");
    }

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = refreshTokenGenerator.generate();
    redisService.saveRefreshTokenForUser(user.getId(), refreshToken);

    return UserRestResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .build();
  }

  @AuditLog
  public UserRestResponse updatePassword(String accessToken, String newPassword) {
    String userId = jwtService.extractUserId(accessToken);
    userService.updatePassword(userId, newPassword);

    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.error("message= User not found for password update, userId={}", userId);
          return new UserNotFoundException(USER_NOT_FOUND_EXCEPTION);
        });

    String newAccessToken = jwtService.generateAccessToken(user);
    String refreshToken = refreshTokenGenerator.generate();
    redisService.saveRefreshTokenForUser(user.getId(), refreshToken);

    return UserRestResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .accessToken(newAccessToken)
        .refreshToken(refreshToken)
        .build();
  }

  public boolean isAccessTokenValid(String accessToken) {
    return jwtService.isAccessTokenValid(accessToken);
  }

  @AuditLog
  public UserInfoRestResponse obtainUserInfoFromToken(String accessToken) {
    return UserInfoRestResponse.builder()
        .id(jwtService.extractUserId(accessToken))
        .username(jwtService.extractUsername(accessToken))
        .email(jwtService.extractEmail(accessToken))
        .build();
  }

  @AuditLog
  public AccessTokenRefreshedResponse generateNewAccessToken(String refreshToken) {
    String userId = redisService.findUserIdByRefreshToken(refreshToken);

    if (userId == null) {
      log.error("message= Refresh token not found in Redis, token={}", refreshToken);
      throw new ForbiddenException(INVALID_REFRESH_TOKEN_EXCEPTION);
    }

    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.error("message= User not found during token refresh, userId={}", userId);
          return new UserNotFoundException(USER_NOT_FOUND_EXCEPTION);
        });

    String accessToken = jwtService.generateAccessToken(user);

    AccessTokenRefreshedResponse response = new AccessTokenRefreshedResponse();
    response.setAccessToken(accessToken);
    return response;
  }
}