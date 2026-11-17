package com.pawmento.pet.adapter.in.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePetRequest(
		@NotBlank(message = "이름은 1~20자로 입력해 주세요.")
		@Size(max = 20, message = "이름은 1~20자로 입력해 주세요.")
		String name,

		@NotBlank(message = "반려동물의 종류를 선택해 주세요.")
		@Pattern(regexp = "DOG|CAT|OTHER", message = "반려동물의 종류를 선택해 주세요.")
		String species,

		@Size(max = 100, message = "품종은 100자까지 입력해 주세요.")
		String breed,

		LocalDate birthday) {
}
