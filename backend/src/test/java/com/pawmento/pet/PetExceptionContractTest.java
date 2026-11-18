package com.pawmento.pet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.global.exception.ResourceNotFoundException;
import com.pawmento.global.exception.RuleViolationException;
import com.pawmento.global.exception.StateConflictException;
import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.application.exception.PetConcurrentModificationException;
import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.exception.PetNotFoundException;
import com.pawmento.pet.domain.exception.InvalidPetProfileException;
import com.pawmento.pet.domain.exception.PetOwnershipViolationException;

class PetExceptionContractTest {
	@Test
	@DisplayName("#72-S6 pet 구체 예외는 공통 기본 4종의 처리 계약에 연결된다")
	void concreteExceptionsUseCommonBases() {
		assertThat(InvalidPetException.class).isAssignableTo(RuleViolationException.class);
		assertThat(InvalidPetProfileException.class).isAssignableTo(RuleViolationException.class);
		assertThat(PetOwnershipViolationException.class).isAssignableTo(PermissionDeniedException.class);
		assertThat(PetNotFoundException.class).isAssignableTo(ResourceNotFoundException.class);
		assertThat(PetHasRecordsException.class).isAssignableTo(StateConflictException.class);
		assertThat(PetConcurrentModificationException.class).isAssignableTo(StateConflictException.class);
	}
}
