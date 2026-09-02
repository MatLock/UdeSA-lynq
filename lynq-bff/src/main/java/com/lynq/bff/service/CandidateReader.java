package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ForbiddenException;
import org.springframework.stereotype.Service;

@Service
public class CandidateReader {

  private static final String CANDIDATE = "CANDIDATE";

  private static final String ONLY_CANDIDATES = "Only users of type CANDIDATE can do this";
  private static final String CALLER_UNKNOWN = "The caller could not be resolved";

  private final LynqBackendClient lynqBackendClient;

  public CandidateReader(LynqBackendClient lynqBackendClient) {
    this.lynqBackendClient = lynqBackendClient;
  }

  public UserResponse read(Caller caller) {
    UserResponse user;
    try {
      user = lynqBackendClient.getUser(caller.requestUuid(), caller.authorization()).getData();
    } catch (RuntimeException e) {
      throw new BadGatewayException(CALLER_UNKNOWN, e);
    }

    if (user == null || !CANDIDATE.equals(user.getUserType())) {
      throw new ForbiddenException(ONLY_CANDIDATES);
    }

    return user;
  }
}
