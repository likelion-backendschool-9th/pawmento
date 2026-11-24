package com.pawmento.pet.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class PetConcurrentModificationException extends StateConflictException {

	public PetConcurrentModificationException() {
		super("다른 요청에서 반려동물을 먼저 수정했습니다. 다시 불러와 주세요.");
	}

	public PetConcurrentModificationException(Throwable cause) {
		super("다른 요청에서 반려동물을 먼저 수정했습니다. 다시 불러와 주세요.", cause);
	}

}
