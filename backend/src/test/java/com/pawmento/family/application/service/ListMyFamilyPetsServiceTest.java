package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.family.application.port.in.ListMyFamilyPetsQuery;
import com.pawmento.family.application.port.in.MyFamilyPetResult;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetSummaryPort;
import com.pawmento.family.application.port.out.PetSummary;
import com.pawmento.family.domain.PetFamilyMember;

/**
 * 목록 순서·빈 목록은 {@code FamilyListScenarioTest.myFamilyPets}가 확인한다.
 * 여기에는 DB로 만들기 어려운, 가족 줄은 있는데 반려동물 요약이 없는 경우만 남긴다.
 */
class ListMyFamilyPetsServiceTest {

	private static final Long ME = 7L;
	private static final Instant EARLY = Instant.parse("2026-11-24T03:00:00Z");
	private static final Instant LATE = Instant.parse("2026-11-25T03:00:00Z");

	private final LoadPetFamilyMemberPort families = mock(LoadPetFamilyMemberPort.class);
	private final LoadPetSummaryPort pets = mock(LoadPetSummaryPort.class);
	private final ListMyFamilyPetsService service = new ListMyFamilyPetsService(families, pets);

	@Test
	@DisplayName("#133-S23 반려동물 요약을 찾지 못한 줄은 목록에서 빠진다")
	void skipsMissingPet() {
		when(families.loadByMemberId(ME)).thenReturn(List.of(
				PetFamilyMember.restore(1L, 10L, ME, EARLY),
				PetFamilyMember.restore(2L, 20L, ME, LATE)));
		when(pets.loadSummary(10L)).thenReturn(Optional.empty());
		when(pets.loadSummary(20L)).thenReturn(Optional.of(new PetSummary(20L, "콩이", "0002")));

		assertThat(service.list(new ListMyFamilyPetsQuery(ME)))
				.extracting(MyFamilyPetResult::petId).containsExactly(20L);
	}
}
