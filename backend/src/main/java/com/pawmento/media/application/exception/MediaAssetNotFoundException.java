package com.pawmento.media.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class MediaAssetNotFoundException extends ResourceNotFoundException {
  public MediaAssetNotFoundException() {
    super("파일을 찾을 수 없어요.");
  }
}
