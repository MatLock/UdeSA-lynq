package com.lynq.bff.client;

import com.lynq.bff.config.DmzPassThroughFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "lynqMlDmz", url = "${lynq.ml.url}",
    configuration = DmzPassThroughFeignConfig.class)
public interface LynqMlDmzClient extends DmzClient {

}
