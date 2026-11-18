package com.pawmento.media.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

public class MediaPersistenceUnavailableException extends StorageUnavailableException {
  public MediaPersistenceUnavailableException() {
    super("파일 정보를 저장하거나 조회하지 못했어요. 잠시 후 다시 시도해 주세요.");
  }

  public MediaPersistenceUnavailableException(Throwable cause) {
    super("파일 정보를 저장하거나 조회하지 못했어요. 잠시 후 다시 시도해 주세요.", cause);
  }
}
