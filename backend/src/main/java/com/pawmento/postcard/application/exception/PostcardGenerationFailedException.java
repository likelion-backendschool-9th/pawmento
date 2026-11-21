package com.pawmento.postcard.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

// 생성 작업 안에서만 쓰고 HTTP 응답으로 나가지 않는다. 사용자에게는 엽서의 실패 안내를 보여 준다.
public class PostcardGenerationFailedException extends StorageUnavailableException {
  public PostcardGenerationFailedException(String reason) {
    super(reason);
  }

  public PostcardGenerationFailedException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
