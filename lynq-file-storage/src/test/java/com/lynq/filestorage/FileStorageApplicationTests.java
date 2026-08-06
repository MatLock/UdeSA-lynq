package com.lynq.filestorage;

import com.lynq.filestorage.enums.StoredFileStatus;
import com.lynq.filestorage.model.StoredFileEntity;
import com.lynq.filestorage.repository.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class FileStorageApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-file-storage";
  private static final String FILES_PATH = CONTEXT_PATH + "/files";
  private static final String UPLOAD_URL_PATH = FILES_PATH + "/upload-url";

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String FILE_NAME = "cv.pdf";
  private static final String CONTENT_TYPE = "application/pdf";
  private static final String FILE_CONTENT = "a fake resume";
  private static final String UNKNOWN_FILE_ID = "00000000-0000-0000-0000-000000000000";

  @LocalServerPort
  private int port;

  @Autowired
  private StoredFileRepository storedFileRepository;

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    storedFileRepository.deleteAll();
  }

  @Test
  void createUploadUrlRegistersThePendingFileAndReturnsAPreSignedPutUrl() throws Exception {
    HttpResponse<String> response = postUploadUrl(FILE_NAME);

    assertThat(response.statusCode(), is(201));
    Map<String, Object> data = data(response);
    String fileId = (String) data.get("fileId");
    assertThat(fileId, is(notNullValue()));
    assertThat((String) data.get("s3Key"), containsString(fileId));
    assertThat((String) data.get("s3Key"), containsString(FILE_NAME));
    assertThat((String) data.get("uploadUrl"), containsString("X-Amz-Signature"));

    Optional<StoredFileEntity> persisted = storedFileRepository.findById(fileId);
    assertThat(persisted.isPresent(), is(true));
    assertThat(persisted.get().getStatus(), is(StoredFileStatus.PENDING));
    assertThat(persisted.get().getFileName(), is(FILE_NAME));
  }

  @Test
  void createUploadUrlReturnsForbiddenWhenRequestUuidHeaderIsMissing() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl() + UPLOAD_URL_PATH))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(uploadRequestBody(FILE_NAME)))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode(), is(403));
    assertThat(storedFileRepository.count(), is(0L));
  }

  @Test
  void createUploadUrlReturnsBadRequestWhenTheFileNameIsBlank() throws Exception {
    HttpResponse<String> response = postUploadUrl("   ");

    assertThat(response.statusCode(), is(400));
    assertThat(storedFileRepository.count(), is(0L));
  }

  @Test
  void uploadedBytesReachTheBucketThroughThePreSignedUrl() throws Exception {
    Map<String, Object> upload = data(postUploadUrl(FILE_NAME));

    int uploadStatus = putBytesToPreSignedUrl((String) upload.get("uploadUrl"));

    assertThat(uploadStatus, is(200));
    assertThat(objectSize((String) upload.get("s3Key")), is((long) FILE_CONTENT.length()));
  }

  @Test
  void confirmUploadMarksTheFileAvailableWithTheContentTypeReportedByS3() throws Exception {
    Map<String, Object> upload = data(postUploadUrl(FILE_NAME));
    putBytesToPreSignedUrl((String) upload.get("uploadUrl"));
    String fileId = (String) upload.get("fileId");

    HttpResponse<String> response = post(FILES_PATH + "/" + fileId + "/confirm");

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = data(response);
    assertThat(data.get("status"), is(StoredFileStatus.AVAILABLE.name()));
    assertThat(data.get("contentType"), is(CONTENT_TYPE));
    assertThat(objectSize((String) upload.get("s3Key")), is((long) FILE_CONTENT.length()));
    assertThat(storedFileRepository.findById(fileId).get().getStatus(), is(StoredFileStatus.AVAILABLE));
  }

  @Test
  void confirmUploadReturnsBadRequestWhenTheBytesWereNeverUploaded() throws Exception {
    Map<String, Object> upload = data(postUploadUrl(FILE_NAME));
    String fileId = (String) upload.get("fileId");

    HttpResponse<String> response = post(FILES_PATH + "/" + fileId + "/confirm");

    assertThat(response.statusCode(), is(400));
    assertThat(storedFileRepository.findById(fileId).get().getStatus(), is(StoredFileStatus.PENDING));
  }

  @Test
  void confirmUploadReturnsNotFoundForAnUnknownFile() throws Exception {
    HttpResponse<String> response = post(FILES_PATH + "/" + UNKNOWN_FILE_ID + "/confirm");

    assertThat(response.statusCode(), is(404));
  }

  @Test
  void createDownloadUrlReturnsAPreSignedUrlThatServesTheStoredBytes() throws Exception {
    Map<String, Object> upload = data(postUploadUrl(FILE_NAME));
    putBytesToPreSignedUrl((String) upload.get("uploadUrl"));
    String fileId = (String) upload.get("fileId");
    post(FILES_PATH + "/" + fileId + "/confirm");

    HttpResponse<String> response = get(FILES_PATH + "/" + fileId + "/download-url");

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = data(response);
    assertThat(data.get("fileId"), is(fileId));
    assertThat(data.get("s3Key"), is(upload.get("s3Key")));
    assertThat(getBytesFromPreSignedUrl((String) data.get("downloadUrl")), is(FILE_CONTENT));
  }

  @Test
  void createDownloadUrlReturnsNotFoundForAnUnknownFile() throws Exception {
    HttpResponse<String> response = get(FILES_PATH + "/" + UNKNOWN_FILE_ID + "/download-url");

    assertThat(response.statusCode(), is(404));
  }

  @Test
  void createDownloadUrlsSignsTheWholeBatchInASingleCall() throws Exception {
    Map<String, Object> first = data(postUploadUrl(FILE_NAME));
    Map<String, Object> second = data(postUploadUrl("avatar.png"));
    putBytesToPreSignedUrl((String) first.get("uploadUrl"));
    putBytesToPreSignedUrl((String) second.get("uploadUrl"));
    String firstId = (String) first.get("fileId");
    String secondId = (String) second.get("fileId");

    HttpResponse<String> response =
        postDownloadUrls(List.of(firstId, secondId, UNKNOWN_FILE_ID));

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = data(response);
    assertThat(data.size(), is(2));
    assertThat(data.containsKey(UNKNOWN_FILE_ID), is(false));
    assertThat(getBytesFromPreSignedUrl((String) data.get(firstId)), is(FILE_CONTENT));
    assertThat(getBytesFromPreSignedUrl((String) data.get(secondId)), is(FILE_CONTENT));
  }

  @Test
  void createDownloadUrlsReturnsBadRequestForAnEmptyBatch() throws Exception {
    HttpResponse<String> response = postDownloadUrls(List.of());

    assertThat(response.statusCode(), is(400));
  }

  @Test
  void createDownloadUrlsReturnsBadRequestWhenTheBatchExceedsTheCap() throws Exception {
    List<String> tooMany = IntStream.range(0, 101)
        .mapToObj(i -> UUID.randomUUID().toString())
        .toList();

    HttpResponse<String> response = postDownloadUrls(tooMany);

    assertThat(response.statusCode(), is(400));
  }

  @Test
  void seededObjectsUploadedOutOfBandAreStillReadableThroughTheDownloadUrl() throws Exception {
    Map<String, Object> upload = data(postUploadUrl(FILE_NAME));
    s3TestClient.putObject(
        PutObjectRequest.builder().bucket(AWS_BUCKET).key((String) upload.get("s3Key")).build(),
        RequestBody.fromString(FILE_CONTENT));
    String fileId = (String) upload.get("fileId");
    post(FILES_PATH + "/" + fileId + "/confirm");

    Map<String, Object> data = data(get(FILES_PATH + "/" + fileId + "/download-url"));

    assertThat(getBytesFromPreSignedUrl((String) data.get("downloadUrl")), is(FILE_CONTENT));
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private String uploadRequestBody(String fileName) {
    return objectMapper.writeValueAsString(Map.of(
        "fileName", fileName,
        "contentType", CONTENT_TYPE));
  }

  private HttpResponse<String> postUploadUrl(String fileName) throws Exception {
    HttpRequest request = requestBuilder(UPLOAD_URL_PATH)
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(uploadRequestBody(fileName)))
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postDownloadUrls(List<String> fileIds) throws Exception {
    HttpRequest request = requestBuilder(FILES_PATH + "/download-urls")
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(
            objectMapper.writeValueAsString(Map.of("fileIds", fileIds))))
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path) throws Exception {
    HttpRequest request = requestBuilder(path)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request = requestBuilder(path).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder requestBuilder(String path) {
    return HttpRequest.newBuilder()
        .uri(URI.create(baseUrl() + path))
        .header(REQUEST_UUID_HEADER, REQUEST_UUID);
  }

  private int putBytesToPreSignedUrl(String uploadUrl) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uploadUrl))
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE)
        .PUT(HttpRequest.BodyPublishers.ofString(FILE_CONTENT, StandardCharsets.UTF_8))
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private String getBytesFromPreSignedUrl(String downloadUrl) throws Exception {
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
  }

  private long objectSize(String s3Key) {
    return s3TestClient.headObject(
        HeadObjectRequest.builder().bucket(AWS_BUCKET).key(s3Key).build()).contentLength();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> data(HttpResponse<String> response) {
    Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
    return (Map<String, Object>) body.get("data");
  }
}
