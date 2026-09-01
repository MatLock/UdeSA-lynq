package com.lynq.bff.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "lynqBackendDmz", url = "${lynq.backend.url}")
public interface LynqBackendDmzClient extends DmzClient {

}
