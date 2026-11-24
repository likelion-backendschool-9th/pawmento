package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class DiaryPetAccessDeniedException extends PermissionDeniedException {
  public DiaryPetAccessDeniedException() {
    super("권한이 없습니다.");
  }
}
