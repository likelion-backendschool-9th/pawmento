package com.pawmento.media.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MediaPetRequest(
    @NotNull(message = "반려동물 petId를 입력해 주세요.") @Positive(message = "petId는 양의 정수로 입력해 주세요.")
        Long petId) {}
