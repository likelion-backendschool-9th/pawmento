package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.CurrentQuestionPromptResult;
import com.pawmento.question.application.port.in.GetCurrentQuestionPromptQuery;
import com.pawmento.question.application.port.in.GetCurrentQuestionPromptUseCase;
import com.pawmento.question.application.port.out.CheckQuestionPetAccessPort;
import com.pawmento.question.application.port.out.QuestionProgressStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCurrentQuestionPromptService implements GetCurrentQuestionPromptUseCase {
  private final CheckQuestionPetAccessPort access;
  private final QuestionProgressStorePort store;

  @Override
  @Transactional(readOnly = true)
  public CurrentQuestionPromptResult get(GetCurrentQuestionPromptQuery query) {
    var mode = access.currentMode(query.memberId(), query.petId());
    return CurrentQuestionPromptValues.result(
        query.petId(), mode, store.findCurrent(query.petId(), mode).orElse(null));
  }
}
