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

class ListMyFamilyPetsServiceTest {

	private static final Long ME = 7L;
	private static final Instant EARLY = Instant.parse("2026-11-24T03:00:00Z");
	private static final Instant LATE = Instant.parse("2026-11-25T03:00:00Z");

	private final LoadPetFamilyMemberPort families = mock(LoadPetFamilyMemberPort.class);
	private final LoadPetSummaryPort pets = mock(LoadPetSummaryPort.class);
	private final ListMyFamilyPetsService service = new ListMyFamilyPetsService(families, pets);

	@Test
	@DisplayName("#133 내가 가족으로 들어간 반려동물을 들어간 순서대로 이름·태그·가입 시각과 함께 준다")
	void listsPetsInJoinOrder() {
		when(families.loadByMemberId(ME)).thenReturn(List.of(
				PetFamilyMember.restore(1L, 10L, ME, EARLY),
				PetFamilyMember.restore(2L, 20L, ME, LATE)));
		when(pets.loadSummary(10L)).thenReturn(Optional.of(new PetSummary(10L, "보리", "0001")));
		when(pets.loadSummary(20L)).thenReturn(Optional.of(new PetSummary(20L, "콩이", "0002")));

		List<MyFamilyPetResult> result = service.list(new ListMyFamilyPetsQuery(ME));

		assertThat(result).containsExactly(
				new MyFamilyPetResult(10L, "보리", "0001", EARLY),
				new MyFamilyPetResult(20L, "콩이", "0002", LATE));
	}

	@Test
	@DisplayName("#133 반려동물 요약을 찾지 못한 줄은 목록에서 빠진다")
	void skipsMissingPet() {
		when(families.loadByMemberId(ME)).thenReturn(List.of(
				PetFamilyMember.restore(1L, 10L, ME, EARLY),
				PetFamilyMember.restore(2L, 20L, ME, LATE)));
		when(pets.loadSummary(10L)).thenReturn(Optional.empty());
		when(pets.loadSummary(20L)).thenReturn(Optional.of(new PetSummary(20L, "콩이", "0002")));

		assertThat(service.list(new ListMyFamilyPetsQuery(ME)))
				.extracting(MyFamilyPetResult::petId).containsExactly(20L);
	}

	@Test
	@DisplayName("#133 가족으로 들어간 반려동물이 없으면 빈 목록이다")
	void emptyWhenNone() {
		when(families.loadByMemberId(ME)).thenReturn(List.of());

		assertThat(service.list(new ListMyFamilyPetsQuery(ME))).isEmpty();
	}
}
