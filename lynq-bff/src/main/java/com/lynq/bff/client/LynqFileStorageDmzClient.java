package com.lynq.bff.client;

import com.lynq.bff.config.DmzPassThroughFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "lynqFileStorageDmz", url = "${lynq.file-storage.url}",
    configuration = DmzPassThroughFeignConfig.class)
public interface LynqFileStorageDmzClient extends DmzClient {

}
