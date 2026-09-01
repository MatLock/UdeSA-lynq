package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DmzProxyControllerImplTest {

  @Test
  void downstreamPathIsTheCallersPathWithoutItsLeadingSlash() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/user/generate-upload-image");

    assertThat(DmzProxyControllerImpl.downstreamPath(request), is("user/generate-upload-image"));
  }

  @Test
  void downstreamPathKeepsPathSegmentsThatLookLikeIds() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/files/0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41/confirm");

    assertThat(DmzProxyControllerImpl.downstreamPath(request),
        is("files/0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41/confirm"));
  }

  @Test
  void downstreamPathHandlesASingleSegmentEndpoint() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/skill-enhance");

    assertThat(DmzProxyControllerImpl.downstreamPath(request), is("skill-enhance"));
  }
}
