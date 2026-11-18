package com.pawmento.pet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.pet.domain.exception.InvalidPetProfileException;

class PetTest {
	private Pet pet() {
		return Pet.create(1L, "콩이", "abcd", PetSpecies.DOG, null, null, LocalDate.of(2026, 11, 17));
	}

	@Test
	@DisplayName("#46-S9 소유자 판단은 도메인이 수행하고 다른 회원을 거절한다")
	void ownershipBelongsToDomain() {
		Pet pet = pet();
		assertThatCode(() -> pet.requireOwnedBy(1L)).doesNotThrowAnyException();
		assertThatThrownBy(() -> pet.requireOwnedBy(2L)).isInstanceOf(PetOwnershipViolationException.class);
		assertThatThrownBy(() -> pet.requireOwnedBy(null)).isInstanceOf(PetOwnershipViolationException.class);
	}

	@Test
	@DisplayName("#46-S10 기록 존재 여부를 받아 도메인이 삭제 가능 여부를 판단한다")
	void recordsBlockDeletion() {
		Pet pet = pet();
		assertThatCode(() -> pet.requireDeletable(false)).doesNotThrowAnyException();
		assertThatThrownBy(() -> pet.requireDeletable(true)).isInstanceOf(PetDeletionBlockedException.class);
		assertThat(pet.getName()).isEqualTo("콩이");
	}

	@Test
	@DisplayName("#46-S7 잘못된 모드는 전용 예외를 던지고 기존 모드를 유지한다")
	void invalidModeKeepsState() {
		Pet pet = pet();
		assertThatThrownBy(() -> pet.changeMode(null)).isInstanceOf(InvalidPetProfileException.class);
		assertThat(pet.getMode()).isEqualTo(PetMode.ACTIVE);
	}
}
