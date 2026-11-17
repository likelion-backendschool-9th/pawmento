package com.pawmento.media.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class MediaLocationConsentUnavailableException extends StateConflictException {
  public MediaLocationConsentUnavailableException() {
    super("위치 사용 동의 절차가 준비되기 전에는 위치 수집을 사용할 수 없어요.");
  }
}
