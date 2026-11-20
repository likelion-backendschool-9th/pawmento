package com.pawmento.map.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.exception.DiaryLocationContractViolation;
import com.pawmento.map.application.port.in.ApplyDiaryLocationCommand;
import com.pawmento.map.application.port.in.ApplyDiaryLocationUseCase;
import com.pawmento.map.application.port.in.DiaryLocationApplyResult;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import com.pawmento.map.domain.DiaryLocation;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApplyDiaryLocationService implements ApplyDiaryLocationUseCase {
	private final DiaryLocationStorePort store;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public DiaryLocationApplyResult apply(ApplyDiaryLocationCommand command) {
		var location = DiaryLocationPortValues.value(command.location());
		var current = store.lock(command.diaryId()).orElse(null);
		var decision =
				DiaryLocation.assess(
						current,
						command.petId(),
						command.createdBy(),
						command.revision(),
						location);
		return switch (decision) {
			case STALE -> DiaryLocationApplyResult.STALE;
			case DUPLICATE -> DiaryLocationApplyResult.DUPLICATE;
			case CONTRACT_VIOLATION -> throw new DiaryLocationContractViolation();
			case APPLIED -> {
				if (command.location() == null) {
					store.remove(command.diaryId());
				} else {
					store.write(
							command.diaryId(),
							command.petId(),
							command.createdBy(),
							command.revision(),
							location);
				}
				yield DiaryLocationApplyResult.APPLIED;
			}
		};
	}
}
