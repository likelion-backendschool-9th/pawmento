package com.pawmento.pet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.global.exception.ResourceNotFoundException;
import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetSpecies;
import com.pawmento.pet.domain.exception.PetOwnershipViolationException;

class PetOwnershipTest {
	private final LoadPetPort load = mock(LoadPetPort.class);

	@Test
	@DisplayName("#72-S2 소유권 예외는 같은 인스턴스로 전달된다")
	void ownershipFailureIsNotWrapped() {
		Pet pet = mock(Pet.class);
		when(load.findById(2L)).thenReturn(Optional.of(pet));
		var failure = new PetOwnershipViolationException();
		doThrow(failure).when(pet).requireOwnedBy(1L);
		assertThatThrownBy(() -> PetOwnership.requireOwnedPet(load, 2L, 1L))
			.isSameAs(failure).isInstanceOf(PermissionDeniedException.class);
	}

	@Test
	@DisplayName("#72-S8 pet 조회 포트는 정상 결과·타인 거절·미존재 계약을 유지한다")
	void queryContractRemainsStable() {
		Pet pet = Pet.restore(2L, 1L, "콩이", "abcd", PetSpecies.DOG, null,
			LocalDate.of(2020, 1, 1), com.pawmento.pet.domain.PetMode.ACTIVE, 0L);
		when(load.findById(2L)).thenReturn(Optional.of(pet));
		when(load.findById(3L)).thenReturn(Optional.empty());
		var service = new GetPetService(java.time.Clock.systemUTC(), load);
		var result = service.get(new GetPetQuery(1L, 2L));
		assertThat(result.petId()).isEqualTo(2L);
		assertThat(result.name()).isEqualTo("콩이");
		assertThatThrownBy(() -> service.get(new GetPetQuery(9L, 2L)))
			.isInstanceOf(PermissionDeniedException.class).hasMessage("권한이 없습니다.");
		assertThatThrownBy(() -> service.get(new GetPetQuery(1L, 3L)))
			.isInstanceOf(ResourceNotFoundException.class).hasMessage("반려동물을 찾을 수 없습니다.");
	}
}
