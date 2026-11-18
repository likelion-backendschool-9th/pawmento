package com.pawmento.media.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

public class MediaStorageUnavailableException extends StorageUnavailableException {
  public MediaStorageUnavailableException() {
    super("파일 저장소를 사용할 수 없어요. 잠시 후 다시 시도해 주세요.");
  }

  public MediaStorageUnavailableException(Throwable cause) {
    super("파일 저장소를 사용할 수 없어요. 잠시 후 다시 시도해 주세요.", cause);
  }
}
