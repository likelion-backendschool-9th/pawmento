package com.pawmento.map.domain;

/**
 * 공공데이터 행 하나를 장소로 바꾼 결과. 장소가 되었거나(draft) 제외된 이유(exclusion) 중 정확히 하나만 있다. 병원·미용(인허가)과
 * 보호센터 변환이 함께 쓴다.
 */
public record PlaceOutcome(PlaceDraft draft, LocalDataExclusion exclusion) {

	public PlaceOutcome {
		// 둘 다 없거나 둘 다 있으면 제외 이유별 건수를 셀 때 값이 어긋나므로 만들 때 막는다
		if ((draft == null) == (exclusion == null)) {
			throw new IllegalArgumentException("draft와 exclusion 중 정확히 하나만 있어야 합니다.");
		}
	}

	public boolean excluded() {
		return draft == null;
	}

}
