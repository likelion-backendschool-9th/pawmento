package com.pawmento.map.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 장소 주소의 가려진 글자와 건물 이름을 다루는 규칙(#239).
 * 행정안전부 원본은 주소의 일부를 `*`로 가려서 보낸다. 가려진 글자는 복원할 수 없다.
 */
public final class PlaceAddress {

	private static final char MASK = '*';

	private PlaceAddress() {
	}

	/** 주소의 어느 위치든 `*`가 있으면 가려진 주소다. 없거나 비어 있으면 가려진 주소가 아니다. */
	public static boolean isMasked(String address) {
		return address != null && address.indexOf(MASK) >= 0;
	}

	/**
	 * 좌표로 찾은 도로명 주소 뒤에 원본 괄호 안의 건물 이름을 붙인다. 괄호 안을 쉼표로 나눠 `*`가 든 말은 빼고,
	 * 남는 말이 없으면 도로명 주소를 그대로 돌려준다. 예: `… 32-14` + `(성수동*가, 갤러리아 포레)` → `… 32-14 (갤러리아 포레)`
	 */
	public static String withBuildingName(String roadAddress, String originalAddress) {
		String buildingName = buildingName(originalAddress);
		return buildingName.isEmpty() ? roadAddress : roadAddress + " (" + buildingName + ")";
	}

	// 마지막 괄호 묶음의 말 중 `*`가 없는 것을 ", "로 잇는다
	private static String buildingName(String address) {
		if (address == null) {
			return "";
		}
		int open = address.lastIndexOf('(');
		int close = address.indexOf(')', open + 1);
		if (open < 0 || close < 0) {
			return "";
		}
		return Arrays.stream(address.substring(open + 1, close).split(","))
			.map(String::strip)
			.filter(part -> !part.isEmpty() && part.indexOf(MASK) < 0)
			.collect(Collectors.joining(", "));
	}

}
