package com.pawmento.map.application.port.out;

import java.util.Optional;

import com.pawmento.map.domain.DiaryLocation;

public interface LoadDiaryLocationPort {

	Optional<DiaryLocation> loadByDiaryId(Long diaryId);

}
