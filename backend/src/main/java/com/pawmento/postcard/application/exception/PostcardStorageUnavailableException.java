package com.pawmento.postcard.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

public class PostcardStorageUnavailableException extends StorageUnavailableException {
  public PostcardStorageUnavailableException(Throwable cause) {
    super("엽서 파일을 저장소에서 다루지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause);
  }
}
