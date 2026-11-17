package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class MediaAccessDeniedException extends PermissionDeniedException {
  public enum Reason {
    WRONG_PET("다른 반려동물의 파일에 접근할 수 없어요."),
    NOT_UPLOADER("본인이 올린 파일만 변경할 수 있어요.");
    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }

  public MediaAccessDeniedException(Reason reason) {
    super(reason.message);
  }
}
