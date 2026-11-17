package com.pawmento.media.application.exception;

import com.pawmento.global.exception.ContentLimitExceededException;

public class MediaRequestLimitExceededException extends ContentLimitExceededException {
  public MediaRequestLimitExceededException() {
    super("요청은 16MiB까지 보낼 수 있어요.");
  }
}
