package com.pawmento.map.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.GetDiaryLocationUseCase;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import com.pawmento.map.domain.DiaryLocationState;
import com.pawmento.map.domain.exception.InvalidDiaryLocationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetDiaryLocationService implements GetDiaryLocationUseCase {
	private final DiaryLocationStorePort store;

	@Override
	@Transactional(readOnly = true)
	public Optional<DiaryLocationState> get(Long diaryId) {
		if (diaryId == null || diaryId <= 0) {
			throw InvalidDiaryLocationException.invalidIdentity();
		}
		return store.read(diaryId);
	}
}
