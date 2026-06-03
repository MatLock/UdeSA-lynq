package com.lynq.iam.aspect;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Log4j2
public class LogAspect {

  private static final String PARAMETER_FORMAT = "%s=%s";
  private static final String PASSWORD_PARAM = "password";

  private final ObjectMapper objectMapper;

  public LogAspect(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Around("@annotation(AuditLog)")
  public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
    Logger log = LogManager.getLogger(joinPoint.getSignature().getDeclaringType());

    CodeSignature codeSignature = (CodeSignature) joinPoint.getSignature();
    String[] parameterNames = codeSignature.getParameterNames();
    Object[] arguments = joinPoint.getArgs();
    List<String> logValues = createParameterList(parameterNames, arguments);
    log.debug("message= Started Method '{}' with parameters '{}'", joinPoint.getSignature().getName(), logValues);
    try{
      Object returnValue = joinPoint.proceed();
      log.debug("message= Finished Method '{}' with parameters '{}'", joinPoint.getSignature().getName(), logValues);
      return returnValue;
    }catch(Exception e){
      log.error("message= Error when executing Method '{}' with parameter '{}'",  joinPoint.getSignature().getName(), logValues, e);
      throw e;
    }
  }

  private List<String> createParameterList(String[] parameterNames, Object[] parameterValues){
    List<String> logParameters = new ArrayList<>();
    for(Integer i = 0; i < parameterNames.length; i++){
      String value;
      if (PASSWORD_PARAM.equalsIgnoreCase(parameterNames[i])) {
        value = "****";
      } else {
        value = serialize(parameterValues[i]);
      }
      logParameters.add(format(PARAMETER_FORMAT, parameterNames[i], value));
    }
    return logParameters;
  }

  private String serialize(Object obj) {
    if (obj == null || obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
      return String.valueOf(obj);
    }
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return String.valueOf(obj);
    }
  }



}
