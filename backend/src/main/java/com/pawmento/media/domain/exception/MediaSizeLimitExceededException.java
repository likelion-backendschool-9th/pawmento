package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.ContentLimitExceededException;

public class MediaSizeLimitExceededException extends ContentLimitExceededException {
  public MediaSizeLimitExceededException() {
    super("파일은 15MiB까지 업로드할 수 있어요.");
  }
}
