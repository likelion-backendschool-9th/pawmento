package com.pawmento.map.domain;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * 동물보호센터 행 하나를 장소로 바꾼다. 위도·경도가 올바른 센터만 장소가 되고, 아니면 이유와 함께 제외한다. 좌표가 이미 WGS84라
 * 변환하지 않는다. 운영 시간이 00:00~24:00이면 24시간(HOURS), 휴무일은 요일·공휴일 글자(`+`로 이음)이거나 단독 연중무휴일 때만 원본 그대로
 * 저장하고 "0" 같은 의미를 알 수 없는 값은 "휴무 없음"으로 풀지 않고 정보 없음(null)으로 둔다. (#172-S7, S8)
 */
public final class ShelterPlaceFactory {

	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private static final String YEAR_ROUND = "연중무휴";

	private static final Set<String> CLOSED_DAY_WORDS = Set.of("월요일", "화요일", "수요일", "목요일", "금요일", "토요일",
			"일요일", "공휴일", YEAR_ROUND);

	private ShelterPlaceFactory() {
	}

	/**
	 * @throws IllegalArgumentException 필수 항목이 빠진 행(호출한 쪽이 isComplete로 먼저 확인한다)
	 */
	public static PlaceOutcome create(ShelterRecord record) {
		if (!record.isComplete()) {
			throw new IllegalArgumentException("필수 항목이 빠진 행입니다. 호출하기 전에 isComplete로 확인해야 합니다.");
		}
		if (record.latitude() == null || record.longitude() == null || record.latitude().isBlank()
				|| record.longitude().isBlank()) {
			return excluded(LocalDataExclusion.NO_COORDINATES);
		}
		double latitude;
		double longitude;
		try {
			latitude = Double.parseDouble(record.latitude().strip());
			longitude = Double.parseDouble(record.longitude().strip());
		}
		catch (NumberFormatException e) {
			return excluded(LocalDataExclusion.OUT_OF_RANGE);
		}
		if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
				|| !new KoreaTmConverter.Wgs84(latitude, longitude).isInKorea()) {
			return excluded(LocalDataExclusion.OUT_OF_RANGE);
		}

		String address = notBlank(record.address());
		Open24hBasis basis = Open24hRule.fromHours(record.openTime(), record.closeTime());
		return new PlaceOutcome(new PlaceDraft(PlaceCategory.SHELTER, record.careRegNo().strip(),
				record.name().strip(), address, PlacePhone.normalize(record.phone(), address), latitude, longitude,
				basis != null, basis, openingHours(record), closedDays(record.closeDay()),
				parseStandardDate(record.dataStdDt())), null);
	}

	private static PlaceOutcome excluded(LocalDataExclusion exclusion) {
		return new PlaceOutcome(null, exclusion);
	}

	// 시작과 끝이 모두 있을 때만 "09:00~18:00"로 만든다. 한쪽만 있으면 운영 시간을 알 수 없다
	private static String openingHours(ShelterRecord record) {
		String open = notBlank(record.openTime());
		String close = notBlank(record.closeTime());
		return open != null && close != null ? open + "~" + close : null;
	}

	private static String closedDays(String value) {
		String closeDay = notBlank(value);
		if (closeDay == null) {
			return null;
		}
		String[] words = closeDay.split("\\+", -1);
		for (String word : words) {
			if (!CLOSED_DAY_WORDS.contains(word.strip())) {
				return null;
			}
		}
		// 연중무휴는 다른 휴무일과 함께 올 수 없다. 모순된 값(연중무휴+일요일)은 의미를 알 수 없어 정보 없음으로 둔다
		if (words.length > 1 && closeDay.contains(YEAR_ROUND)) {
			return null;
		}
		return closeDay;
	}

	private static String notBlank(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	// 읽을 수 없는 기준일은 센터를 버릴 이유가 아니므로 null로 둔다
	private static Instant parseStandardDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.strip()).atStartOfDay(KOREA).toInstant();
		}
		catch (DateTimeException e) {
			return null;
		}
	}

}
