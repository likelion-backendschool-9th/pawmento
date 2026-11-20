package com.pawmento.map.application.port.in;

public interface ApplyDiaryLocationUseCase {
  DiaryLocationApplyResult apply(ApplyDiaryLocationCommand command);
}
