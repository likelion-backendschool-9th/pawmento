package com.pawmento.media.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class MediaPetAccessDeniedException extends PermissionDeniedException {
  public MediaPetAccessDeniedException() {
    super("권한이 없습니다.");
  }
}
