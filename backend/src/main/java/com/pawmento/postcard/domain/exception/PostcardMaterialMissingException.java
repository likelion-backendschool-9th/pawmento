package com.pawmento.postcard.domain.exception;

import com.pawmento.global.exception.StateConflictException;

public class PostcardMaterialMissingException extends StateConflictException {
  public PostcardMaterialMissingException() {
    super("그림으로 만들 내용이 부족해요. 다른 기록을 고르거나 설명을 더해 주세요.");
  }
}
