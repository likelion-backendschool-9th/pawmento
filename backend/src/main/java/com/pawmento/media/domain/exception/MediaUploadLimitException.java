package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.StateConflictException;

public class MediaUploadLimitException extends StateConflictException {
  public MediaUploadLimitException(int limit) {
    super("처리 중인 파일은 " + limit + "개까지 보관할 수 있어요.");
  }
}
