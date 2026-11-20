package com.pawmento.map.domain;

/** 저장 상태 관찰값. 손상된 메타데이터도 정정/삭제할 수 있도록 입력 검증을 적용하지 않는다. */
public record DiaryLocationState(
		Long rowId,
		Long diaryId,
		Long petId,
		Long createdBy,
		double latitude,
		double longitude,
		String placeName,
		LocationSource source,
		Long sourceAssetId,
		long revision) {
	public DiaryLocationState {
		latitude = latitude == 0 ? 0 : latitude;
		longitude = longitude == 0 ? 0 : longitude;
	}

}
