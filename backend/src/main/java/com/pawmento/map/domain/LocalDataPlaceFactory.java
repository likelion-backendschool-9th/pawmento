package com.pawmento.map.domain;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 인허가 행 하나를 장소로 바꾼다. 영업/정상이고 좌표가 올바른 행만 장소가 되고, 아니면 이유와 함께 제외한다.
 * 좌표는 도쿄 측지계 보정을 포함해 위도·경도로 바꾸고({@link KoreaTmConverter}), 전화번호는 걸 수 있는 번호만 남기며
 * ({@link PlacePhone}), 병원 이름에 "24시"가 있으면 24시로 표시한다({@link Open24hRule}). (#172-S1, S3, S6, S7)
 */
public final class LocalDataPlaceFactory {

	private static final String ACTIVE_STATUS = "영업/정상";

	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private static final DateTimeFormatter UPDATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private LocalDataPlaceFactory() {
	}

	/** 장소가 되었거나(draft) 제외된 이유(exclusion) 중 정확히 하나만 있다. */
	public record Outcome(PlaceDraft draft, LocalDataExclusion exclusion) {

		public Outcome {
			// 둘 다 없거나 둘 다 있으면 제외 이유별 건수를 셀 때 값이 어긋나므로 만들 때 막는다
			if ((draft == null) == (exclusion == null)) {
				throw new IllegalArgumentException("draft와 exclusion 중 정확히 하나만 있어야 합니다.");
			}
		}

		public boolean excluded() {
			return draft == null;
		}
	}

	/**
	 * @param category 병원(HOSPITAL) 또는 미용(GROOMING). 보호센터는 다른 API라 이 규칙으로 만들지 않는다
	 * @throws IllegalArgumentException 종류가 맞지 않거나 필수 항목이 빠진 행(호출한 쪽이 isComplete로 먼저 확인한다)
	 */
	public static Outcome create(PlaceCategory category, LocalDataRecord record) {
		if (category != PlaceCategory.HOSPITAL && category != PlaceCategory.GROOMING) {
			throw new IllegalArgumentException("인허가 데이터는 병원과 미용만 만듭니다: " + category);
		}
		if (!record.isComplete()) {
			throw new IllegalArgumentException("필수 항목이 빠진 행입니다. 호출하기 전에 isComplete로 확인해야 합니다.");
		}
		if (!ACTIVE_STATUS.equals(record.businessStatus().strip())) {
			return excluded(LocalDataExclusion.CLOSED);
		}
		if (record.x().isBlank() || record.y().isBlank()) {
			return excluded(LocalDataExclusion.NO_COORDINATES);
		}
		double x;
		double y;
		try {
			x = Double.parseDouble(record.x().strip());
			y = Double.parseDouble(record.y().strip());
		}
		catch (NumberFormatException e) {
			return excluded(LocalDataExclusion.OUT_OF_RANGE);
		}
		if (!Double.isFinite(x) || !Double.isFinite(y)) {
			return excluded(LocalDataExclusion.OUT_OF_RANGE);
		}
		KoreaTmConverter.Wgs84 point = KoreaTmConverter.toWgs84(x, y);
		if (!point.isInKorea()) {
			return excluded(LocalDataExclusion.OUT_OF_RANGE);
		}

		String address = firstNotBlank(record.roadAddress(), record.lotAddress());
		// 이름의 "24시" 판단은 병원 데이터로만 확인했다(병원 이름에 24가 든 195곳 중 190곳이 24시). 미용은 확인하지 못해 보지 않는다
		Open24hBasis basis = category == PlaceCategory.HOSPITAL ? Open24hRule.fromName(record.name()) : null;
		return new Outcome(new PlaceDraft(category, record.sourceId().strip(), record.name().strip(), address,
				PlacePhone.normalize(record.phone(), address), point.latitude(), point.longitude(), basis != null,
				basis, null, null, parseUpdatedAt(record.updatedAt())), null);
	}

	private static Outcome excluded(LocalDataExclusion exclusion) {
		return new Outcome(null, exclusion);
	}

	private static String firstNotBlank(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first.strip();
		}
		return second != null && !second.isBlank() ? second.strip() : null;
	}

	// 읽을 수 없는 시각은 장소를 버릴 이유가 아니므로 null로 둔다
	private static Instant parseUpdatedAt(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value.strip(), UPDATED_AT).atZone(KOREA).toInstant();
		}
		catch (DateTimeException e) {
			return null;
		}
	}

}
