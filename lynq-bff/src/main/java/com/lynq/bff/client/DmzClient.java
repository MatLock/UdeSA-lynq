package com.lynq.bff.client;

import feign.Response;
import java.util.Collection;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

public interface DmzClient {

  String DMZ_PATH = "/dmz/{path}";
  String PATH_VARIABLE = "path";

  @GetMapping(DMZ_PATH)
  Response get(@PathVariable(PATH_VARIABLE) String path,
               @RequestParam Map<String, Collection<String>> query,
               @RequestHeader Map<String, Collection<String>> headers);

  @PostMapping(DMZ_PATH)
  Response post(@PathVariable(PATH_VARIABLE) String path,
                @RequestParam Map<String, Collection<String>> query,
                @RequestHeader Map<String, Collection<String>> headers,
                @RequestBody byte[] body);

  @PutMapping(DMZ_PATH)
  Response put(@PathVariable(PATH_VARIABLE) String path,
               @RequestParam Map<String, Collection<String>> query,
               @RequestHeader Map<String, Collection<String>> headers,
               @RequestBody byte[] body);

  @PatchMapping(DMZ_PATH)
  Response patch(@PathVariable(PATH_VARIABLE) String path,
                 @RequestParam Map<String, Collection<String>> query,
                 @RequestHeader Map<String, Collection<String>> headers,
                 @RequestBody byte[] body);

  @DeleteMapping(DMZ_PATH)
  Response delete(@PathVariable(PATH_VARIABLE) String path,
                  @RequestParam Map<String, Collection<String>> query,
                  @RequestHeader Map<String, Collection<String>> headers);
}
