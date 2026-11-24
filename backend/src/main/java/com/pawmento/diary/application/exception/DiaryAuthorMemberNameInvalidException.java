package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

// 회원 모듈에 저장된 닉네임이 일기 작성자 이름 규칙에 맞지 않는다. 사용자가 보낸 값이 아니라 서버 데이터 문제다.
public class DiaryAuthorMemberNameInvalidException extends StoredDataInvalidException {
  public DiaryAuthorMemberNameInvalidException(Throwable cause) {
    super("회원 이름이 일기 작성자 이름 규칙에 맞지 않습니다.", cause);
  }
}
