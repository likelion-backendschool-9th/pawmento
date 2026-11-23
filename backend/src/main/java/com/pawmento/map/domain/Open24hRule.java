package com.pawmento.map.domain;

/**
 * 24시간 운영으로 볼 수 있는 근거를 판단한다. 근거가 없으면 null이다.
 * 병원 이름에 "24시"가 있으면 NAME(이름 기준 추정, 검증된 영업시간이 아니다), 제공된 운영 시간이 00:00~24:00이면
 * HOURS다. "진월24동물병원"의 24는 24시간이 아니므로 "24시"가 들어 있을 때만 본다(#172-S7).
 */
public final class Open24hRule {

	private Open24hRule() {
	}

	public static Open24hBasis fromName(String name) {
		return name != null && name.contains("24시") ? Open24hBasis.NAME : null;
	}

	public static Open24hBasis fromHours(String openingTime, String closingTime) {
		if (openingTime == null || closingTime == null) {
			return null;
		}
		return "00:00".equals(openingTime.strip()) && "24:00".equals(closingTime.strip()) ? Open24hBasis.HOURS : null;
	}

}
