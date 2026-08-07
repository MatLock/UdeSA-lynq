package com.lynq.backend.client.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Asks lynq-file-storage to sign a batch of download URLs in one round-trip. Ids it does not know
 * are omitted from the response instead of failing the batch.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFileDownloadBatchRequest {

  private List<String> fileIds;

}
