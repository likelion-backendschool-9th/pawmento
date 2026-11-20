package com.pawmento.map.application.port.in;

import java.util.Optional;



public interface GetDiaryLocationUseCase {
	Optional<DiaryLocationSnapshot> get(Long diaryId);
}
