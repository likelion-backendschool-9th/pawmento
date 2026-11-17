package com.pawmento.media.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class MediaUploadLimitException extends StateConflictException {
  public MediaUploadLimitException() {
    super("처리 중인 파일은 5개까지 보관할 수 있어요.");
  }
}
