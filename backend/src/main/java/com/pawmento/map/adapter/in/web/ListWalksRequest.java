package com.pawmento.map.adapter.in.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;

/**
 * 날짜별 산책 조회. 쿼리 문자열로 받는다. 날짜는 YYYY-MM-DD만 받고, 달력에 없는 날(2026-02-30)과 다른 형식은 400이다.
 */
record ListWalksRequest(
		@NotNull(message = "petId를 입력해 주세요.") Long petId,
		@NotNull(message = "date를 입력해 주세요.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
}
