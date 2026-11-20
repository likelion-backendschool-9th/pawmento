package com.pawmento.map.application.port.in;

import com.pawmento.map.application.exception.DiaryLocationContractViolation;

/** 초기화된 일기 원본 기준의 명시적 정정/삭제. expected null은 행 없음이다. */
public record ReconcileDiaryLocationCommand(
		Long diaryId,
		Long petId,
		Long createdBy,
		long revision,
		DiaryLocationSnapshot expected,
		DiaryLocationInput location) {
	public ReconcileDiaryLocationCommand {
		ApplyDiaryLocationCommand.validate(diaryId, petId, createdBy, revision);
		if (expected != null && !diaryId.equals(expected.diaryId())) {
			throw new DiaryLocationContractViolation();
		}
	}
}
