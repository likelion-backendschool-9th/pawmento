package com.pawmento.map.application.port.in;

import com.pawmento.map.domain.DiaryLocationValue;
import com.pawmento.map.domain.exception.InvalidDiaryLocationException;

public record ApplyDiaryLocationCommand(
		Long diaryId, Long petId, Long createdBy, long revision, DiaryLocationValue location) {
	public ApplyDiaryLocationCommand {
		validate(diaryId, petId, createdBy, revision);
	}

	static void validate(Long diary, Long pet, Long author, long revision) {
		if (diary == null
				|| diary <= 0
				|| pet == null
				|| pet <= 0
				|| author == null
				|| author <= 0
				|| revision < 0) {
			throw InvalidDiaryLocationException.invalidIdentity();
		}
	}
}
