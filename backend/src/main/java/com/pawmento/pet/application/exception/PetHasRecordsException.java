package com.pawmento.pet.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class PetHasRecordsException extends StateConflictException {
  public PetHasRecordsException() {
    super("기록이 있는 반려동물은 삭제할 수 없습니다.");
  }

  public PetHasRecordsException(Throwable cause) {
    super("기록이 있는 반려동물은 삭제할 수 없습니다.", cause);
  }
}
