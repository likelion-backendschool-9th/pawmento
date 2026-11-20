package com.pawmento.map.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.GetDiaryLocationUseCase;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import com.pawmento.map.application.port.in.DiaryLocationSnapshot;
import com.pawmento.map.application.exception.DiaryLocationContractViolation;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetDiaryLocationService implements GetDiaryLocationUseCase {
	private final DiaryLocationStorePort store;

	@Override
	@Transactional(readOnly = true)
	public Optional<DiaryLocationSnapshot> get(Long diaryId) {
		if (diaryId == null || diaryId <= 0) {
			throw new DiaryLocationContractViolation();
		}
		return store.read(diaryId).map(DiaryLocationPortValues::snapshot);
	}
}
