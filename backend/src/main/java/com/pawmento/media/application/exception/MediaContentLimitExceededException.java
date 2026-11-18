package com.pawmento.media.application.exception;

import com.pawmento.global.exception.ContentLimitExceededException;

public class MediaContentLimitExceededException extends ContentLimitExceededException {
  public MediaContentLimitExceededException() {
    super("파일은 15MiB까지 업로드할 수 있어요.");
  }
}
