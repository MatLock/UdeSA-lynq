package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.DmzClient;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import com.lynq.bff.filter.JwtSignatureFilter;
import feign.Request;
import feign.RetryableException;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class DmzProxyServiceTest {

  private static final String DOWNSTREAM_PATH = "user/generate-upload-image";
  private static final String VERIFIED_USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String SPOOFED_USER_ID = "99999999-9999-9999-9999-999999999999";
  private static final String RESPONSE_BODY = """
      {"success": true}""";

  @Mock
  private DmzClient dmzClient;

  private DmzProxyService dmzProxyService;

  @BeforeEach
  void setUp() {
    dmzProxyService = new DmzProxyService();
  }

  @Test
  void relaysTheStatusAndBodyTheDmzServiceReturned() throws Exception {
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any()))
        .thenReturn(dmzResponse(418, Map.of("content-type", List.of("application/json"))));

    ResponseEntity<byte[]> response =
        dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, getRequest());

    assertThat(response.getStatusCode().value(), is(418));
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8), is(RESPONSE_BODY));
    assertThat(response.getHeaders().getFirst("content-type"), is("application/json"));
  }

  @Test
  void forwardsTheQueryStringAsAQueryMap() throws Exception {
    MockHttpServletRequest request = getRequest();
    request.setParameter("file-name", "avatar.png");
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    assertThat(capturedQuery(), hasEntry(is("file-name"), contains("avatar.png")));
  }

  @Test
  void keepsRepeatedQueryParameterValues() throws Exception {
    MockHttpServletRequest request = getRequest();
    request.setParameter("skills", "Java", "Spring");
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    assertThat(capturedQuery(), hasEntry(is("skills"), containsInAnyOrder("Java", "Spring")));
  }

  @Test
  void forwardsTheCallersHeadersButNotTheHopByHopOnes() throws Exception {
    MockHttpServletRequest request = getRequest();
    request.addHeader("Authorization", "Bearer token");
    request.addHeader("lynq-request-uuid", "550e8400-e29b-41d4-a716-446655440000");
    request.addHeader("Host", "lynq.local");
    request.addHeader("Connection", "keep-alive");
    request.addHeader("Content-Length", "0");
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    Map<String, Collection<String>> headers = capturedHeaders();
    assertThat(headers, hasEntry(is("Authorization"), contains("Bearer token")));
    assertThat(headers.containsKey("Host"), is(false));
    assertThat(headers.containsKey("Connection"), is(false));
    assertThat(headers.containsKey("Content-Length"), is(false));
  }

  @Test
  void dropsTheDownstreamRequestUuidHeaderSoItIsNotSentTwice() throws Exception {
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200,
        Map.of("lynq-request-uuid", List.of("550e8400-e29b-41d4-a716-446655440000"),
            "content-type", List.of("application/json"))));

    ResponseEntity<byte[]> response =
        dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, getRequest());

    assertThat(response.getHeaders().containsHeader("lynq-request-uuid"), is(false));
    assertThat(response.getHeaders().getFirst("content-type"), is("application/json"));
  }

  @Test
  void dropsTheDownstreamContentLengthSoTheContainerRecomputesIt() throws Exception {
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any()))
        .thenReturn(dmzResponse(200, Map.of("content-length", List.of("99999"))));

    ResponseEntity<byte[]> response =
        dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, getRequest());

    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH), is(nullValue()));
  }

  @Test
  void leavesABodilessAnswerWithoutABody() throws Exception {
    Response noContent = Response.builder()
        .status(204)
        .request(dummyRequest())
        .headers(Map.of())
        .build();
    when(dmzClient.delete(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(noContent);
    MockHttpServletRequest request = getRequest();
    request.setMethod("DELETE");

    ResponseEntity<byte[]> response = dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    assertThat(response.getStatusCode().value(), is(204));
    assertThat(response.getBody(), is(nullValue()));
  }

  @Test
  void passesThePostBodyOnAsRawBytes() throws Exception {
    String body = """
        {"title": "Senior Backend Engineer"}""";
    MockHttpServletRequest request = getRequest();
    request.setMethod("POST");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    when(dmzClient.post(eq(DOWNSTREAM_PATH), any(), any(), any()))
        .thenReturn(dmzResponse(201, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(dmzClient).post(eq(DOWNSTREAM_PATH), any(), any(), bodyCaptor.capture());
    assertThat(new String(bodyCaptor.getValue(), StandardCharsets.UTF_8), is(body));
  }

  @Test
  void forwardsTheVerifiedUserIdAsTheUserIdHeader() throws Exception {
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, getRequest());

    assertThat(capturedHeaders(), hasEntry(is("user-id"), contains(VERIFIED_USER_ID)));
  }

  @Test
  void replacesAClientSuppliedUserIdWithTheVerifiedOne() throws Exception {
    MockHttpServletRequest request = getRequest();
    request.addHeader("user-id", SPOOFED_USER_ID);
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    Map<String, Collection<String>> headers = capturedHeaders();
    assertThat(headers, hasEntry(is("user-id"), contains(VERIFIED_USER_ID)));
    assertThat(headers.values().stream().flatMap(Collection::stream).toList(),
        not(hasItem(SPOOFED_USER_ID)));
  }

  @Test
  void replacesAClientSuppliedUserIdWhateverItsCasing() throws Exception {
    MockHttpServletRequest request = getRequest();
    request.addHeader("User-Id", SPOOFED_USER_ID);
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    assertThat(capturedHeaders().values().stream().flatMap(Collection::stream).toList(),
        not(hasItem(SPOOFED_USER_ID)));
  }

  @Test
  void dropsAClientSuppliedCompanyIdOutright() throws Exception {
    MockHttpServletRequest request = getRequest();
    request.addHeader("company-id", "22222222-2222-2222-2222-222222222222");
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenReturn(dmzResponse(200, Map.of()));

    dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request);

    assertThat(capturedHeaders().containsKey("company-id"), is(false));
  }

  @Test
  void turnsAnUnreachableDmzServiceIntoABadGateway() {
    when(dmzClient.get(eq(DOWNSTREAM_PATH), any(), any())).thenThrow(connectionFailure());

    MockHttpServletRequest request = getRequest();

    assertThrows(BadGatewayException.class,
        () -> dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request));
  }

  @Test
  void rejectsAVerbItDoesNotRelay() {
    MockHttpServletRequest request = getRequest();
    request.setMethod("HEAD");

    assertThrows(MethodNotAllowedException.class,
        () -> dmzProxyService.forward(dmzClient, DOWNSTREAM_PATH, request));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Collection<String>> capturedQuery() {
    ArgumentCaptor<Map<String, Collection<String>>> captor = ArgumentCaptor.forClass(Map.class);
    verify(dmzClient).get(eq(DOWNSTREAM_PATH), captor.capture(), any());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Collection<String>> capturedHeaders() {
    ArgumentCaptor<Map<String, Collection<String>>> captor = ArgumentCaptor.forClass(Map.class);
    verify(dmzClient).get(eq(DOWNSTREAM_PATH), any(), captor.capture());
    return captor.getValue();
  }

  private static MockHttpServletRequest getRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/backend/" + DOWNSTREAM_PATH);
    request.setContent(new byte[0]);

    request.setAttribute(JwtSignatureFilter.VERIFIED_USER_ID, VERIFIED_USER_ID);
    return request;
  }

  private static Response dmzResponse(int status, Map<String, Collection<String>> headers) {
    return Response.builder()
        .status(status)
        .request(dummyRequest())
        .headers(headers)
        .body(RESPONSE_BODY, StandardCharsets.UTF_8)
        .build();
  }

  private static RetryableException connectionFailure() {
    return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET,
        new IOException("refused"), (Long) null, dummyRequest());
  }

  private static Request dummyRequest() {
    return Request.create(Request.HttpMethod.GET, "http://localhost/lynq-backend-app/dmz/user",
        Collections.emptyMap(), Request.Body.empty(), null);
  }
}
