package com.pawmento.postcard.domain.exception;

import com.pawmento.global.exception.StateConflictException;

public class PostcardModeNotAllowedException extends StateConflictException {
  public PostcardModeNotAllowedException() {
    super("엽서는 정원 모드에서 만들 수 있어요.");
  }
}
