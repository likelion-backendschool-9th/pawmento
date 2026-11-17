package com.pawmento.pet.application.exception;

public class PetConcurrentModificationException extends RuntimeException {

	public PetConcurrentModificationException() {
		super("다른 요청에서 반려동물을 먼저 수정했습니다. 다시 불러와 주세요.");
	}

}
