package com.pawmento.pet.domain;

public class PetDeletionBlockedException extends RuntimeException {
	public PetDeletionBlockedException() {
		super("기록이 있는 반려동물은 삭제할 수 없습니다.");
	}
}
