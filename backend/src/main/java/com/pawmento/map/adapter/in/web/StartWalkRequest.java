package com.pawmento.map.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

// petId는 정수로만 받는다. Long으로 바로 받으면 Jackson 기본 설정이 10.9처럼 소수로 온 값을 10으로 잘라 읽어서,
// 요청자가 소유한 10번 반려동물의 산책이 만들어질 수 있다. 소수로 읽어 소수 부분이 있거나 Long 범위를 넘으면 거절한다
record StartWalkRequest(
		@NotNull(message = "petId를 입력해 주세요.")
		@LongInteger(message = "petId는 정수로 입력해 주세요.")
		BigDecimal petId) {

	// @LongInteger를 통과한 값만 부르므로 소수 부분이 없고 Long 범위 안이다
	Long petIdValue() {
		return petId.longValueExact();
	}

}
