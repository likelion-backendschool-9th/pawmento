package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class DiaryPetNotFoundException extends ResourceNotFoundException {
  public DiaryPetNotFoundException() {
    super("일기를 작성할 반려동물을 찾을 수 없어요.");
  }

  public DiaryPetNotFoundException(Throwable cause) {
    super("일기를 작성할 반려동물을 찾을 수 없어요.", cause);
  }
}
