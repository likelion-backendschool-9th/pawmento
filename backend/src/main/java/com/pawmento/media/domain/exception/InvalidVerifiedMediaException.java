package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidVerifiedMediaException extends RuleViolationException {
  public enum Reason {
    FILE("검증한 파일 정보가 올바르지 않아요."),
    IMAGE("검증한 이미지 크기가 올바르지 않아요."),
    AUDIO("검증한 음성 정보가 올바르지 않아요."),
    THUMBNAIL("검증한 썸네일 크기가 올바르지 않아요.");
    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }

  public InvalidVerifiedMediaException(Reason reason) {
    super(reason.message);
  }
}
