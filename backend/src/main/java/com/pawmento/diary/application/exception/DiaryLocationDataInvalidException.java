package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

public final class DiaryLocationDataInvalidException extends StoredDataInvalidException {
  public DiaryLocationDataInvalidException(String message) {
    super(message, null);
  }

  public DiaryLocationDataInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
