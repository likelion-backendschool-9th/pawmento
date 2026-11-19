package com.pawmento.map.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 산책을 날짜별로 나누는 하루. 날짜는 Asia/Seoul 기준이고, 산책이 속하는 날은 시작한 시각이 속한 날이다.
 * 자정을 넘겨 끝난 산책도 시작한 날에 속한다.
 */
public record WalkDay(LocalDate date) {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	public WalkDay {
		if (date == null) {
			throw new IllegalArgumentException("날짜가 필요합니다.");
		}
	}

	// 하루의 시작(포함)
	public Instant startInclusive() {
		return date.atStartOfDay(ZONE).toInstant();
	}

	// 하루의 끝(미포함). 다음 날 0시다
	public Instant endExclusive() {
		return date.plusDays(1).atStartOfDay(ZONE).toInstant();
	}

}
