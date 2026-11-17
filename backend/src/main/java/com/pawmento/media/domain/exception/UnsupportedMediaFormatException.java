package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.UnsupportedFormatException;

public class UnsupportedMediaFormatException extends UnsupportedFormatException {
  public UnsupportedMediaFormatException() {
    super("사진과 음성 녹음 파일만 업로드할 수 있어요.");
  }
}
