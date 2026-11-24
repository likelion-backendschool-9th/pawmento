package com.pawmento.pet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.pawmento.pet.application.port.in.ListPetsQuery;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.domain.*;

class PetBirthYearListTest {
	@Test
	@DisplayName("#178-S2 목록 조회 도중 한국 연도가 바뀌어도 모든 아이의 나이는 같은 시점 기준이다")
	void oneInstantForWholeList() {
		LoadPetPort pets = mock(LoadPetPort.class);
		Pet pet = Pet.create(1L, "보리", "abcd", PetSpecies.DOG, null, null, 2020, LocalDate.of(2026, 12, 31));
		when(pets.findByOwnerMemberId(1L)).thenReturn(List.of(pet, pet));
		AtomicInteger reads = new AtomicInteger();
		Clock clock = new Clock() {
			public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
			public Clock withZone(ZoneId zone) { return this; }
			public Instant instant() {
				return Instant.parse(reads.getAndIncrement() == 0 ? "2026-12-31T14:59:59Z" : "2026-12-31T15:00:00Z");
			}
		};
		var results = new ListPetsService(clock, pets).list(new ListPetsQuery(1L));
		assertThat(results).extracting(r -> r.ageByYear()).containsExactly(6, 6);
		assertThat(reads.get()).isEqualTo(1);
	}
}
