package com.lynq.bff.client;

import com.lynq.bff.config.DmzPassThroughFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "lynqBackendDmz", url = "${lynq.backend.url}",
    configuration = DmzPassThroughFeignConfig.class)
public interface LynqBackendDmzClient extends DmzClient {

}
