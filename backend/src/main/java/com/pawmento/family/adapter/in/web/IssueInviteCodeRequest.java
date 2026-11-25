package com.pawmento.family.adapter.in.web;

import jakarta.validation.constraints.NotNull;

public record IssueInviteCodeRequest(
    @NotNull(message = "반려동물을 선택해 주세요.")
    Long petId) {
}
