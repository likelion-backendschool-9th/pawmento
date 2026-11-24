package com.pawmento.pet.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.pawmento.pet.domain.exception.InvalidPetProfileException;

class PetBirthYearTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

	@Test
	@DisplayName("#178-S1 연도만 알면 날짜 없이 저장하고 나이는 조회 기준 연도로 계산한다")
	void yearOnly() {
		Pet pet = Pet.create(1L, "보리", "abcd", PetSpecies.DOG, null, null, 2020, TODAY);
		assertThat(pet.getBirthday()).isNull();
		assertThat(pet.getBirthYear()).isEqualTo(2020);
		assertThat(pet.ageByYear(TODAY)).isEqualTo(6);
		assertThat(pet.ageByYear(TODAY.plusYears(1))).isEqualTo(7);
	}

	@Test
	@DisplayName("#178-S3 날짜와 연도를 같이 입력해도 날짜만 저장하고 기존 과거 날짜는 보존한다")
	void exactDate() {
		Pet pet = Pet.create(1L, "보리", "abcd", PetSpecies.DOG, null, LocalDate.of(2020, 5, 12), 2020, TODAY);
		assertThat(pet.getBirthYear()).isNull();
		assertThat(pet.effectiveBirthYear()).isEqualTo(2020);
		Pet old = Pet.create(1L, "보리", "old", PetSpecies.DOG, null, LocalDate.of(1899, 12, 31), TODAY);
		assertThat(old.effectiveBirthYear()).isEqualTo(1899);
	}

	@Test
	@DisplayName("#178-S3 복원할 때 날짜와 연도 전용 값의 동시 저장 상태를 거절한다")
	void cannotRestoreBoth() {
		assertThatThrownBy(() -> Pet.restore(1L, 1L, "보리", "abcd", PetSpecies.DOG, null,
				LocalDate.of(2020, 5, 12), PetMode.ACTIVE, 0L, null, 2020))
				.isInstanceOf(InvalidPetProfileException.class);
	}

	@Test
	@DisplayName("#178-S4 같은 연도는 날짜를 보존하고 다른 연도·정확한 날짜로 전환한다")
	void transitions() {
		Pet pet = Pet.create(1L, "보리", "abcd", PetSpecies.DOG, null, LocalDate.of(2020, 5, 12), TODAY);
		pet.edit(null, null, null, null, 2020, TODAY);
		assertThat(pet.getBirthday()).isEqualTo(LocalDate.of(2020, 5, 12));
		pet.edit(null, null, null, null, 2021, TODAY);
		assertThat(pet.getBirthday()).isNull();
		assertThat(pet.getBirthYear()).isEqualTo(2021);
		pet.edit(null, null, null, LocalDate.of(2022, 3, 4), null, TODAY);
		assertThat(pet.getBirthYear()).isNull();
		assertThat(pet.getBirthday()).isEqualTo(LocalDate.of(2022, 3, 4));
	}

	@Test
	@DisplayName("#178-S5 잘못된 출생 정보로 수정하면 이름·품종·출생 정보를 바꾸지 않는다")
	void invalidEditIsAtomicInDomain() {
		Pet pet = Pet.create(1L, "보리", "abcd", PetSpecies.DOG, "기존", null, 2020, TODAY);
		for (int year : new int[] {-1, 0, 1899, 2027}) {
			assertThatThrownBy(() -> pet.edit("바꿀이름", PetSpecies.CAT, "바꿀품종", null, year, TODAY))
					.isInstanceOf(InvalidPetProfileException.class);
		}
		assertThatThrownBy(() -> pet.edit("바꿀이름", null, null, LocalDate.of(2020, 5, 12), 2021, TODAY))
				.isInstanceOf(InvalidPetProfileException.class);
		assertThat(pet.getName()).isEqualTo("보리");
		assertThat(pet.getSpecies()).isEqualTo(PetSpecies.DOG);
		assertThat(pet.getBreed()).isEqualTo("기존");
		assertThat(pet.getBirthYear()).isEqualTo(2020);
	}
}
