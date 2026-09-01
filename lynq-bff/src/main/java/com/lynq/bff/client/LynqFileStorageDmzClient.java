package com.lynq.bff.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "lynqFileStorageDmz", url = "${lynq.file-storage.url}")
public interface LynqFileStorageDmzClient extends DmzClient {

}
