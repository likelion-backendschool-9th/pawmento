package com.pawmento.community.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

// 목록 조회 조건. 쿼리 문자열로 받는다. 형식(필수, 범위)은 여기서 검사하고,
// 카테고리가 있는지와 쓰는 중인지는 서비스가 저장소를 보고 검사한다.
// 범위를 벗어나면 보정하지 않고 거절한다. 프론트엔드는 정해진 값만 보내므로 잘못된 호출을 드러낸다
record ListPostsRequest(
		@NotBlank(message = "카테고리를 선택해 주세요.")
		String category,

		@Min(value = 1, message = SIZE_MESSAGE)
		@Max(value = MAX_SIZE, message = SIZE_MESSAGE)
		Integer size,

		@Min(value = 1, message = "cursor는 1 이상으로 보내 주세요.")
		Long cursor) {

	static final int DEFAULT_SIZE = 20;
	static final int MAX_SIZE = 50;
	private static final String SIZE_MESSAGE = "size는 1~50 사이로 보내 주세요.";

	// size를 보내지 않으면 20개씩 준다
	int sizeOrDefault() {
		return size == null ? DEFAULT_SIZE : size;
	}

}
