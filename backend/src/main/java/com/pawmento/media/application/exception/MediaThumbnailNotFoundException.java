package com.pawmento.media.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class MediaThumbnailNotFoundException extends ResourceNotFoundException {
  public MediaThumbnailNotFoundException() {
    super("썸네일이 없어요.");
  }
}
