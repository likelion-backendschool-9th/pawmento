package com.pawmento.pet.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePetModeRequest(
		@NotBlank(message = "모드를 선택해 주세요.")
		@Pattern(regexp = "ACTIVE|GARDEN", message = "모드를 선택해 주세요.")
		String mode) {
}
