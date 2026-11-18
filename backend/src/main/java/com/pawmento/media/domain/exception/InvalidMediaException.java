package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidMediaException extends RuleViolationException {
  public enum Reason {
    IDENTITY_REQUIRED("회원과 반려동물 정보가 필요합니다."),
    INVALID_FILENAME("올바른 파일 이름을 입력해 주세요."),
    EMPTY_FILE("비어 있는 파일은 업로드할 수 없어요."),
    INVALID_PURPOSE("첨부 용도를 확인해 주세요."),
    INVALID_STORAGE_KEY("파일 저장 키가 올바르지 않아요."),
    SIZE_MISMATCH("요청한 크기와 실제 파일 크기가 달라요.");
    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }

  public InvalidMediaException(Reason reason) {
    super(reason.message);
  }
}
