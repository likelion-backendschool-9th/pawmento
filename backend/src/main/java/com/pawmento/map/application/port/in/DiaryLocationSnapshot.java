package com.pawmento.map.application.port.in;

/** 정정에 쓰는 관측값. 손상된 값도 그대로 되돌려 보낼 수 있어야 한다. */
public record DiaryLocationSnapshot(
		Long rowId,
		Long diaryId,
		Long petId,
		Long createdBy,
		double latitude,
		double longitude,
		String placeName,
		Source source,
		Long sourceAssetId,
		long revision) {
	public enum Source {
		MANUAL
	}
}
