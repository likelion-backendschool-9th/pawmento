package com.pawmento.media.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class MediaRequestDeniedException extends PermissionDeniedException {
  public enum Reason {
    UNSAFE_ORIGIN("허용된 화면에서 파일을 업로드해 주세요."),
    LOGIN_REQUIRED("로그인이 필요합니다.");
    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }

  public MediaRequestDeniedException(Reason reason) {
    super(reason.message);
  }
}
