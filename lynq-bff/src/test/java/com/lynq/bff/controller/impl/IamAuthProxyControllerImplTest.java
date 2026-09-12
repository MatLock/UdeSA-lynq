package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IamAuthProxyControllerImplTest {

  @Test
  void authPathIsTheCallersPathWithoutTheAuthPrefix() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/auth/register");

    assertThat(IamAuthProxyControllerImpl.authPath(request), is("register"));
  }

  @Test
  void authPathKeepsTheSegmentsBelowAuth() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/auth/login/username");

    assertThat(IamAuthProxyControllerImpl.authPath(request), is("login/username"));
  }

  @Test
  void authPathLeavesAPathThatDoesNotHangOffAuthAlone() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath("/user/resume");

    assertThat(IamAuthProxyControllerImpl.authPath(request), is("/user/resume"));
  }
}
