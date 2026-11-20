package com.pawmento.map.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.DiaryLocationApplyResult;
import com.pawmento.map.application.port.in.ReconcileDiaryLocationCommand;
import com.pawmento.map.application.port.in.ReconcileDiaryLocationUseCase;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import com.pawmento.map.domain.DiaryLocation;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReconcileDiaryLocationService implements ReconcileDiaryLocationUseCase {
	private final DiaryLocationStorePort store;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public DiaryLocationApplyResult reconcile(ReconcileDiaryLocationCommand command) {
		var current = store.lock(command.diaryId()).orElse(null);
		var decision = DiaryLocation.assessReconciliation(current, command.expected(), command.location());
		if (decision != DiaryLocation.ApplyDecision.APPLIED) {
			return DiaryLocationApplyResult.valueOf(decision.name());
		}
		if (command.location() == null) {
			store.remove(command.diaryId());
		} else {
			store.write(
					command.diaryId(),
					command.petId(),
					command.createdBy(),
					command.revision(),
					command.location());
		}
		return DiaryLocationApplyResult.APPLIED;
	}
}
