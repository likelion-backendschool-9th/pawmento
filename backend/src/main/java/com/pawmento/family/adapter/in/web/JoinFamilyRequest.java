package com.pawmento.family.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinFamilyRequest(
    @NotBlank(message = "초대 코드를 입력해 주세요.")
    @Size(max = 20, message = "초대 코드를 확인해 주세요.")
    String code) {
}
