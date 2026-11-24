package com.pawmento.media.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class MediaPetNotFoundException extends ResourceNotFoundException {
  public MediaPetNotFoundException() {
    super("반려동물을 찾을 수 없습니다.");
  }
}
