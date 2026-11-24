package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

// 일기 작성자로 저장할 회원이 없다. 작성자가 아니어서 거부하는 DiaryAuthorDeniedException과 다르다.
public class DiaryAuthorMemberNotFoundException extends ResourceNotFoundException {
  public DiaryAuthorMemberNotFoundException(Throwable cause) {
    super("일기 작성자를 찾을 수 없어요.", cause);
  }
}
