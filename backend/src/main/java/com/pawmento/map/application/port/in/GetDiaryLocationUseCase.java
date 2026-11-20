package com.pawmento.map.application.port.in;

import java.util.Optional;

import com.pawmento.map.domain.DiaryLocationState;

public interface GetDiaryLocationUseCase {
	Optional<DiaryLocationState> get(Long diaryId);
}
