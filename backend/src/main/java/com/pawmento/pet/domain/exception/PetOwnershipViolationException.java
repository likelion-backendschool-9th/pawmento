package com.pawmento.pet.domain.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class PetOwnershipViolationException extends PermissionDeniedException {
	public PetOwnershipViolationException() {
		super("권한이 없습니다.");
	}
}
