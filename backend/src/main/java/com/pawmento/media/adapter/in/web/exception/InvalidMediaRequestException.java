package com.pawmento.media.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidMediaRequestException extends RuleViolationException {
  public enum Reason {
    SINGLE_FILE_REQUIRED("한 요청에는 file 파일 하나만 보내 주세요."),
    INVALID_PET_QUERY("petId 쿼리를 양의 정수 하나로 보내 주세요."),
    INVALID_PATH("파일 경로를 확인해 주세요.");
    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }

  public InvalidMediaRequestException(Reason reason) {
    super(reason.message);
  }
}
