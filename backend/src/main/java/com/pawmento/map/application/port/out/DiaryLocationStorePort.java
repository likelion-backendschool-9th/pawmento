package com.pawmento.map.application.port.out;

import java.util.Optional;

import com.pawmento.map.domain.DiaryLocationState;
import com.pawmento.map.domain.DiaryLocationValue;

public interface DiaryLocationStorePort {
	Optional<DiaryLocationState> read(Long diaryId);

	Optional<DiaryLocationState> lock(Long diaryId);

	void write(
			Long diaryId, Long petId, Long createdBy, long revision, DiaryLocationValue location);

	void remove(Long diaryId);
}
