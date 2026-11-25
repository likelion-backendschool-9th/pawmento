package com.pawmento.family.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 가족 목록 조회 조건. 쿼리 문자열(?petId=)로 받는다
record ListFamilyRequest(
    @NotNull(message = "반려동물을 선택해 주세요.")
    @Positive(message = "petId는 1 이상으로 보내 주세요.")
    Long petId) {
}
