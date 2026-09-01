package com.lynq.bff.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "lynqMlDmz", url = "${lynq.ml.url}")
public interface LynqMlDmzClient extends DmzClient {

}
