package com.pawmento.pet.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

public final class StoredPetDataInvalidException extends StoredDataInvalidException {
	public StoredPetDataInvalidException(Long petId, Throwable cause) {
		super("반려동물 출생 정보를 복원할 수 없습니다. petId=" + petId, cause);
	}
}
