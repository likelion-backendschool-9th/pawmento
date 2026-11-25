package com.pawmento.pet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.pet.application.port.in.GetPetSummaryQuery;
import com.pawmento.pet.application.port.in.PetSummaryResult;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.domain.Pet;

class GetPetSummaryServiceTest {

	private final LoadPetPort load = mock(LoadPetPort.class);
	private final GetPetSummaryService service = new GetPetSummaryService(load);

	@Test
	@DisplayName("#133-S3 요청자 검사 없이 반려동물의 id·이름·태그만 돌려준다")
	void returnsSummary() {
		Pet pet = mock(Pet.class);
		when(pet.getId()).thenReturn(2L);
		when(pet.getName()).thenReturn("보리");
		when(pet.getTag()).thenReturn("0001");
		when(load.findById(2L)).thenReturn(Optional.of(pet));

		assertThat(service.get(new GetPetSummaryQuery(2L)))
				.contains(new PetSummaryResult(2L, "보리", "0001"));
	}

	@Test
	@DisplayName("#133 없는 반려동물이면 빈 값이다")
	void missingPet() {
		when(load.findById(2L)).thenReturn(Optional.empty());

		assertThat(service.get(new GetPetSummaryQuery(2L))).isEmpty();
	}
}
