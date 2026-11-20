package com.pawmento.map.application.port.in;

public interface ReconcileDiaryLocationUseCase {
	DiaryLocationApplyResult reconcile(ReconcileDiaryLocationCommand command);
}
