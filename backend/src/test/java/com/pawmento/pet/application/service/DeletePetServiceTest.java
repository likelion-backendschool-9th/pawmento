package com.pawmento.pet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.global.exception.StateConflictException;
import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.port.in.DeletePetCommand;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.DeletePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetSpecies;

class DeletePetServiceTest {
	@Test
	@DisplayName("#46-S10 기록이 있으면 삭제를 호출하지 않고 원본을 보존한다 (고침 #72: 기록 조회에 따른 삭제 거절을 애플리케이션에서 판단)")
	void recordsBlockDeletion() {
		Pet pet = Pet.create(1L, "콩이", "abcd", PetSpecies.DOG, null, null, LocalDate.of(2026, 11, 17));
		AtomicReference<Pet> stored = new AtomicReference<>(pet);
		LoadPetPort load = load(stored);
		DeletePetPort delete = mock(DeletePetPort.class);
		var service = new DeletePetService(load, delete, id -> true);
		assertThatThrownBy(() -> service.delete(new DeletePetCommand(1L, 2L)))
			.isInstanceOf(PetHasRecordsException.class).isInstanceOf(StateConflictException.class)
			.hasMessage("기록이 있는 반려동물은 삭제할 수 없습니다.");
		verifyNoInteractions(delete);
		assertThat(stored.get()).isSameAs(pet);
		assertThat(stored.get().getName()).isEqualTo("콩이");
	}

	@Test
	@DisplayName("#72-S4 기록이 없으면 해당 반려동물을 삭제한다")
	void noRecordsAllowsDeletion() {
		var stored = new AtomicReference<>(Pet.create(1L, "콩이", "abcd", PetSpecies.DOG, null, null,
			LocalDate.of(2026, 11, 17)));
		DeletePetPort delete = mock(DeletePetPort.class);
		new DeletePetService(load(stored), delete, id -> false).delete(new DeletePetCommand(1L, 2L));
		verify(delete).deleteById(2L);
		verifyNoMoreInteractions(delete);
	}

	private LoadPetPort load(AtomicReference<Pet> stored) {
		return new LoadPetPort() {
			public Optional<Pet> findById(Long id) { return Optional.ofNullable(stored.get()); }
			public List<Pet> findByOwnerMemberId(Long id) { return List.of(stored.get()); }
			public boolean existsByOwnerMemberIdAndTag(Long id, String tag) { return false; }
		};
	}
}
