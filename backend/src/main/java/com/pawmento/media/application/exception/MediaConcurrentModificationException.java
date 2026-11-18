package com.pawmento.media.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class MediaConcurrentModificationException extends StateConflictException {
  public MediaConcurrentModificationException(Throwable cause) {
    super("파일 상태가 변경됐어요. 다시 확인해 주세요.", cause);
  }
}
