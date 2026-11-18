package com.pawmento.diary.domain.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class DiaryAuthorDeniedException extends PermissionDeniedException {
  public DiaryAuthorDeniedException() {
    super("작성한 일기만 변경할 수 있어요.");
  }
}
