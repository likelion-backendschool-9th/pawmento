package com.pawmento.media.adapter.in.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    var request = new MockHttpServletRequest("POST", "/api/v1/pets/%31/media/%32/content");
    filter.doFilter(
        request, new MockHttpServletResponse(), (r, s) -> fail("encoded path bypassed guard"));
    assertThat(error.get())
        .isInstanceOfSatisfying(
            MediaUploadException.class,
            e -> assertThat(e.kind()).isEqualTo(MediaUploadException.Kind.FORBIDDEN));
    verifyNoInteractions(check);
  }
}
