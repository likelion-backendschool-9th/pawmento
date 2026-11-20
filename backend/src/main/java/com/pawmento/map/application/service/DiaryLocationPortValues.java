package com.pawmento.map.application.service;

import com.pawmento.global.exception.RuleViolationException;
import com.pawmento.map.application.exception.DiaryLocationContractViolation;
import com.pawmento.map.application.port.in.DiaryLocationInput;
import com.pawmento.map.application.port.in.DiaryLocationSnapshot;
import com.pawmento.map.domain.DiaryLocationState;
import com.pawmento.map.domain.DiaryLocationValue;
import com.pawmento.map.domain.LocationSource;

final class DiaryLocationPortValues {
	private DiaryLocationPortValues() {}

	static DiaryLocationValue value(DiaryLocationInput input) {
		if (input == null) {
			return null;
		}
		try {
			return new DiaryLocationValue(input.latitude(), input.longitude(), input.placeName());
		} catch (RuleViolationException cause) {
			throw new DiaryLocationContractViolation(cause);
		}
	}

	static DiaryLocationState state(DiaryLocationSnapshot snapshot) {
		return snapshot == null
				? null
				: new DiaryLocationState(
						snapshot.rowId(),
						snapshot.diaryId(),
						snapshot.petId(),
						snapshot.createdBy(),
						snapshot.latitude(),
						snapshot.longitude(),
						snapshot.placeName(),
						snapshot.source() == null ? null : LocationSource.valueOf(snapshot.source().name()),
						snapshot.sourceAssetId(),
						snapshot.revision());
	}

	static DiaryLocationSnapshot snapshot(DiaryLocationState state) {
		return new DiaryLocationSnapshot(
				state.rowId(),
				state.diaryId(),
				state.petId(),
				state.createdBy(),
				state.latitude(),
				state.longitude(),
				state.placeName(),
				state.source() == null ? null : DiaryLocationSnapshot.Source.valueOf(state.source().name()),
				state.sourceAssetId(),
				state.revision());
	}
}
