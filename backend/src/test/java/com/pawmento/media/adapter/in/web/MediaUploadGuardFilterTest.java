package com.pawmento.media.adapter.in.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.media.adapter.in.web.exception.MediaRequestDeniedException;
import com.pawmento.media.application.port.in.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.ModelAndView;

class MediaUploadGuardFilterTest {
  @Test
  @DisplayName("#33-S17 URL 인코딩된 파일 경로도 헤더 검사에서 빠져나가지 않는다")
  void encodedPath() throws Exception {
    var check = mock(CheckMediaUploadUseCase.class);
    var error = new AtomicReference<Exception>();
    var filter =
        new MediaUploadGuardFilter(
            check,
            (request, response, handler, e) -> {
              error.set(e);
              return new ModelAndView();
            });
    var request = new MockHttpServletRequest("POST", "/api/v1/media/%32/content");
    filter.doFilter(
        request, new MockHttpServletResponse(), (r, s) -> fail("encoded path bypassed guard"));
    assertThat(error.get()).isInstanceOf(MediaRequestDeniedException.class);
    verifyNoInteractions(check);
  }

  private static MockHttpServletRequest request(String query) {
    var request =
        new MockHttpServletRequest("POST", "/api/v1/media/%32/content") {
          @Override
          public String getParameter(String name) {
            throw new AssertionError("multipart parameter access before guard");
          }

          @Override
          public String[] getParameterValues(String name) {
            throw new AssertionError("multipart parameter access before guard");
          }

          @Override
          public java.util.Map<String, String[]> getParameterMap() {
            throw new AssertionError("multipart parameter access before guard");
          }
        };
    request.setQueryString(query);
    request.setUserPrincipal(() -> "3");
    request.addHeader("Origin", "http://localhost");
    request.addHeader("X-Pawmento-Upload", "1");
    return request;
  }

  @Test
  @DisplayName("#33-S17 도메인 루트의 petId는 raw query에서 읽고 multipart 파싱 전에 소유권을 확인한다")
  void rawQueryBeforeParsing() throws Exception {
    var check = mock(CheckMediaUploadUseCase.class);
    var filter =
        new MediaUploadGuardFilter(
            check,
            (r, s, h, e) -> {
              throw new AssertionError(e);
            });
    var passed = new java.util.concurrent.atomic.AtomicBoolean();
    filter.doFilter(
        request("petId=%31"), new MockHttpServletResponse(), (r, s) -> passed.set(true));
    verify(check).check(new CheckMediaUploadQuery(3L, 1L, 2L));
    assertThat(passed.get()).isTrue();
  }

  @Test
  @DisplayName("#33-S17 petId 누락·중복·0·잘못된 인코딩은 파일 본문을 읽지 않고 거부한다")
  void invalidPetQuery() throws Exception {
    for (String query :
        new String[] {
          "",
          "petId=1&petId=2",
          "petId=0",
          "petId=-1",
          "petId=abc",
          "petId=%XX",
          "petId=1&%70etId=2"
        }) {
      var check = mock(CheckMediaUploadUseCase.class);
      var error = new AtomicReference<Exception>();
      var filter =
          new MediaUploadGuardFilter(
              check,
              (r, s, h, e) -> {
                error.set(e);
                return new ModelAndView();
              });
      filter.doFilter(
          request(query),
          new MockHttpServletResponse(),
          (r, s) -> fail("invalid query passed guard"));
      assertThat(error.get())
          .isInstanceOf(
              com.pawmento.media.adapter.in.web.exception.InvalidMediaRequestException.class);
      verifyNoInteractions(check);
    }
  }
}
