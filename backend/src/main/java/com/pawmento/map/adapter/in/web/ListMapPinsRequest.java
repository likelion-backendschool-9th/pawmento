package com.pawmento.map.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 지도 화면에 보이는 영역과 반려동물. 쿼리 문자열로 받는다.
 * 형식(필수, 범위)은 여기서 검사하고, 최솟값이 최댓값보다 큰지는 도메인(MapArea)이 검사한다.
 */
record ListMapPinsRequest(
		@NotNull(message = "petId를 입력해 주세요.") Long petId,
		@NotNull(message = "minLatitude를 입력해 주세요.") @Latitude Double minLatitude,
		@NotNull(message = "minLongitude를 입력해 주세요.") @Longitude Double minLongitude,
		@NotNull(message = "maxLatitude를 입력해 주세요.") @Latitude Double maxLatitude,
		@NotNull(message = "maxLongitude를 입력해 주세요.") @Longitude Double maxLongitude) {
}
