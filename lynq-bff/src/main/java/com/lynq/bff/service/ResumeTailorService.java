package com.lynq.bff.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.client.LynqAgentClient;
import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.request.ApplyJobRequest;
import com.lynq.bff.client.request.MarkConversationAppliedRequest;
import com.lynq.bff.client.request.StartTailorConversationRequest;
import com.lynq.bff.client.request.TailorJobRequest;
import com.lynq.bff.client.request.TailorTurnRequest;
import com.lynq.bff.client.response.JobDetailsResponse;
import com.lynq.bff.client.response.UserResumeResponse;
import com.lynq.bff.controller.response.ResumeTailorApplyRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import com.lynq.bff.exceptions.ConflictException;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.exceptions.NotFoundException;
import feign.FeignException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class ResumeTailorService {

  private static final String DEFAULT_LANGUAGE = "EN";
  private static final String JOB_ID_FIELD = "jobId";
  private static final String STATUS_FIELD = "status";

  private static final String RESUME_ID_REQUIRED = "A resume id is required";
  private static final String MESSAGE_REQUIRED = "A message is required";
  private static final String TURN_KEY_REQUIRED = "A turn key is required";
  private static final String RESUMES_UNREADABLE = "The caller's resumes could not be read";
  private static final String RESUME_NOT_FOUND = "Resume '%s' not found";
  private static final String JOB_UNREADABLE = "Job posting '%s' could not be read";
  private static final String JOB_NOT_FOUND = "Job posting '%s' not found";
  private static final String CONVERSATION_NOT_STARTED =
      "The tailoring conversation could not be started";
  private static final String TURN_FAILED = "The tailoring turn could not be completed";
  private static final String CONVERSATION_UNREADABLE =
      "The tailoring conversation could not be read";
  private static final String CONVERSATION_WITHOUT_JOB =
      "The tailoring conversation does not carry the job posting it was started for";
  private static final String APPLY_FAILED = "The application could not be created";
  private static final String CONVERSATION_NOT_CLOSED =
      "The tailoring conversation could not be closed";

  private final LynqBackendClient lynqBackendClient;
  private final LynqAgentClient lynqAgentClient;
  private final ObjectMapper objectMapper;

  public ResumeTailorService(LynqBackendClient lynqBackendClient,
                             LynqAgentClient lynqAgentClient,
                             ObjectMapper objectMapper) {
    this.lynqBackendClient = lynqBackendClient;
    this.lynqAgentClient = lynqAgentClient;
    this.objectMapper = objectMapper;
  }

  public Object start(String resumeId, String jobId, String language, Caller caller) {
    UserResumeResponse baseResume = ownedResume(resumeId, caller);
    JobDetailsResponse job = readJob(jobId, caller);

    log.info("message= Starting resume tailoring, user_id={}, resume_id={}, job_id={}",
        caller.userId(), resumeId, jobId);

    StartTailorConversationRequest request = StartTailorConversationRequest.builder()
        .job(jobPayload(job, jobId))
        .baseResumeId(resumeId)
        .baseResume(baseResume.getResume())
        .language(normalized(language))
        .resumeLanguage(normalized(baseResume.getLanguage()))
        .build();

    return relay(
        () -> lynqAgentClient.startConversation(request, caller.requestUuid(), caller.userId())
            .getData(),
        CONVERSATION_NOT_STARTED);
  }

  public Object turn(String conversationId, String message, String turnKey, Caller caller) {
    if (message == null || message.isBlank()) {
      throw new BadRequestException(MESSAGE_REQUIRED);
    }
    if (turnKey == null || turnKey.isBlank()) {
      throw new BadRequestException(TURN_KEY_REQUIRED);
    }

    TailorTurnRequest request = TailorTurnRequest.builder()
        .message(message.trim())
        .turnKey(turnKey.trim())
        .build();

    log.info("message= Started tailoring turn, user_id={}, conversation_id={}, turn_key={}",
        caller.userId(), conversationId, request.getTurnKey());

    return relay(
        () -> lynqAgentClient
            .takeTurn(conversationId, request, caller.requestUuid(), caller.userId())
            .getData(),
        TURN_FAILED);
  }

  public Map<String, Object> view(String conversationId, Caller caller) {
    return readConversation(conversationId, caller);
  }

  public ResumeTailorApplyRestResponse apply(String conversationId, String resumeId,
                                             Caller caller) {
    if (resumeId == null || resumeId.isBlank()) {
      throw new BadRequestException(RESUME_ID_REQUIRED);
    }

    ownedResume(resumeId, caller);
    Map<String, Object> conversation = readConversation(conversationId, caller);
    String jobId = jobIdOf(conversation);

    log.info("message= Closing a tailoring conversation, user_id={}, conversation_id={}, "
        + "job_id={}, resume_id={}", caller.userId(), conversationId, jobId, resumeId);

    Applied applied = applyToJob(jobId, resumeId, caller);

    return ResumeTailorApplyRestResponse.builder()
        .application(applied.application())
        .alreadyApplied(applied.alreadyApplied())
        .conversationStatus(markApplied(conversationId, resumeId, caller))
        .build();
  }

  private UserResumeResponse ownedResume(String resumeId, Caller caller) {
    List<UserResumeResponse> resumes;
    try {
      resumes = lynqBackendClient
          .getUserResumes(caller.requestUuid(), caller.authorization())
          .getData();
    } catch (RuntimeException e) {
      throw new BadGatewayException(RESUMES_UNREADABLE, e);
    }

    return (resumes == null ? List.<UserResumeResponse>of() : resumes).stream()
        .filter(resume -> resumeId != null && resumeId.equals(resume.getId()))
        .findFirst()
        .orElseThrow(() -> new BadRequestException(String.format(RESUME_NOT_FOUND, resumeId)));
  }

  private JobDetailsResponse readJob(String jobId, Caller caller) {
    JobDetailsResponse job;
    try {
      job = lynqBackendClient
          .getJobDetails(jobId, caller.requestUuid(), caller.authorization())
          .getData();
    } catch (FeignException e) {
      if (e.status() == 404) {
        throw new NotFoundException(String.format(JOB_NOT_FOUND, jobId));
      }
      throw new BadGatewayException(String.format(JOB_UNREADABLE, jobId), e);
    } catch (RuntimeException e) {
      throw new BadGatewayException(String.format(JOB_UNREADABLE, jobId), e);
    }

    if (job == null) {
      throw new NotFoundException(String.format(JOB_NOT_FOUND, jobId));
    }
    return job;
  }

  private TailorJobRequest jobPayload(JobDetailsResponse job, String jobId) {
    return TailorJobRequest.builder()
        .id(job.getJobId() == null ? jobId : job.getJobId())
        .title(job.getTitle())
        .description(job.getDescription())
        .company(job.getCompany() == null ? null : job.getCompany().getName())
        .workType(job.getWorkType())
        .skills(job.getSkills() == null ? List.of() : job.getSkills())
        .build();
  }

  private Map<String, Object> readConversation(String conversationId, Caller caller) {
    return relay(
        () -> lynqAgentClient
            .getConversation(conversationId, caller.requestUuid(), caller.userId())
            .getData(),
        CONVERSATION_UNREADABLE);
  }

  private String jobIdOf(Map<String, Object> conversation) {
    Object jobId = conversation == null ? null : conversation.get(JOB_ID_FIELD);
    if (jobId == null || jobId.toString().isBlank()) {
      throw new BadGatewayException(CONVERSATION_WITHOUT_JOB);
    }
    return jobId.toString();
  }

  private Applied applyToJob(String jobId, String resumeId, Caller caller) {
    ApplyJobRequest request = ApplyJobRequest.builder().resumeId(resumeId).build();

    try {
      Object application = lynqBackendClient
          .applyToJob(jobId, request, caller.requestUuid(), caller.authorization())
          .getData();
      return new Applied(application, false);
    } catch (FeignException e) {
      if (e.status() == 400) {
        log.info("message= The candidate had already applied to this job, user_id={}, job_id={}",
            caller.userId(), jobId);
        return new Applied(null, true);
      }
      throw new BadGatewayException(APPLY_FAILED, e);
    } catch (RuntimeException e) {
      throw new BadGatewayException(APPLY_FAILED, e);
    }
  }

  private String markApplied(String conversationId, String resumeId, Caller caller) {
    MarkConversationAppliedRequest request = MarkConversationAppliedRequest.builder()
        .appliedResumeId(resumeId)
        .build();

    try {
      Map<String, Object> closed = lynqAgentClient
          .markApplied(conversationId, request, caller.requestUuid(), caller.userId())
          .getData();
      Object status = closed == null ? null : closed.get(STATUS_FIELD);
      return status == null ? null : status.toString();
    } catch (FeignException e) {
      if (e.status() == 409) {
        throw translated(e, CONVERSATION_NOT_CLOSED);
      }
      log.warn("message= Applied to the job but the tailoring conversation stays open, "
          + "user_id={}, conversation_id={}", caller.userId(), conversationId, e);
      return null;
    } catch (RuntimeException e) {
      log.warn("message= Applied to the job but the tailoring conversation stays open, "
          + "user_id={}, conversation_id={}", caller.userId(), conversationId, e);
      return null;
    }
  }

  private <T> T relay(Supplier<T> call, String failureMessage) {
    try {
      return call.get();
    } catch (FeignException e) {
      throw translated(e, failureMessage);
    } catch (RuntimeException e) {
      throw new BadGatewayException(failureMessage, e);
    }
  }

  private RuntimeException translated(FeignException failure, String failureMessage) {
    String reason = reasonOf(failure, failureMessage);

    return switch (failure.status()) {
      case 400 -> new BadRequestException(reason);
      case 403 -> new ForbiddenException(reason);
      case 404 -> new NotFoundException(reason);
      case 409 -> new ConflictException(reason, codeOf(failure));
      default -> new BadGatewayException(failureMessage, failure);
    };
  }

  private String reasonOf(FeignException failure, String fallback) {
    String reason = field(failure, "reason");
    return reason == null ? fallback : reason;
  }

  private String codeOf(FeignException failure) {
    return field(failure, "code");
  }

  private String field(FeignException failure, String name) {
    String body = failure.contentUTF8();
    if (body == null || body.isBlank()) {
      return null;
    }

    try {
      JsonNode value = objectMapper.readTree(body).get(name);
      return value == null || value.isNull() ? null : value.asText();
    } catch (RuntimeException | JsonProcessingException e) {
      log.warn("message= The agent's error body could not be read, status={}",
          failure.status(), e);
      return null;
    }
  }

  private String normalized(String language) {
    return language == null || language.isBlank()
        ? DEFAULT_LANGUAGE
        : language.trim().toUpperCase(Locale.ROOT);
  }

  private record Applied(Object application, boolean alreadyApplied) {
  }
}
