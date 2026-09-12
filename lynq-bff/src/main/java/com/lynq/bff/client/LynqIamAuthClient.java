package com.lynq.bff.client;

import com.lynq.bff.config.DmzPassThroughFeignConfig;
import feign.Response;
import java.util.Collection;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The auth API of lynq-iam, as the gateway relays it. Unlike the services behind the DMZ prefix,
 * lynq-iam is not behind one: it mints and checks the tokens itself, so it keeps validating every
 * credential it is handed and this client adds nothing to what crosses.
 *
 * <p>Only the verbs the relayed auth surface uses are declared — GET for the availability checks,
 * POST for registration, the logins and the refresh, PATCH for the password update. It is a
 * separate interface rather than a {@link DmzClient} sibling because the path it hangs off is
 * {@code /auth}, not {@code /dmz}, and because Feign allows a client interface only one level of
 * inheritance.
 */
@FeignClient(name = "lynqIamAuth", url = "${lynq.iam.url}",
    configuration = DmzPassThroughFeignConfig.class)
public interface LynqIamAuthClient {

  String AUTH_PATH = "/auth/{path}";
  String PATH_VARIABLE = "path";

  @GetMapping(AUTH_PATH)
  Response get(@PathVariable(PATH_VARIABLE) String path,
               @RequestParam Map<String, Collection<String>> query,
               @RequestHeader Map<String, Collection<String>> headers);

  @PostMapping(AUTH_PATH)
  Response post(@PathVariable(PATH_VARIABLE) String path,
                @RequestParam Map<String, Collection<String>> query,
                @RequestHeader Map<String, Collection<String>> headers,
                @RequestBody byte[] body);

  @PatchMapping(AUTH_PATH)
  Response patch(@PathVariable(PATH_VARIABLE) String path,
                 @RequestParam Map<String, Collection<String>> query,
                 @RequestHeader Map<String, Collection<String>> headers,
                 @RequestBody byte[] body);
}
