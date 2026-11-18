package com.pawmento.media.adapter.in.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.media.adapter.in.web.exception.MediaInputUnavailableException;
import java.io.*;
import org.junit.jupiter.api.*;
import org.springframework.web.multipart.MultipartFile;

class MultipartMediaContentTest {
  @Test
  @DisplayName("#33-S17 업로드 claim 전에는 multipart 스트림을 열지 않고 사용 후 닫는다")
  void lazyOpenAndClose() throws Exception {
    var file = mock(MultipartFile.class);
    var input = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(input);
    try (var content = new MultipartMediaContent(file)) {
      verifyNoInteractions(file);
      content.read();
      verify(file).getInputStream();
    }
    verify(input).close();
  }

  @Test
  @DisplayName("#33-S16 입력 스트림 닫기 실패는 원인을 보존한 애플리케이션 예외로 전달한다")
  void closeFailure() throws Exception {
    var file = mock(MultipartFile.class);
    var input = mock(InputStream.class);
    var cause = new IOException("private path");
    when(file.getInputStream()).thenReturn(input);
    doThrow(cause).when(input).close();
    var content = new MultipartMediaContent(file);
    content.read();
    assertThatThrownBy(content::close)
        .isInstanceOf(MediaInputUnavailableException.class)
        .hasCause(cause)
        .hasMessageNotContaining("private path");
  }
}
