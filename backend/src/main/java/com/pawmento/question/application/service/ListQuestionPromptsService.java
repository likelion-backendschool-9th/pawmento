package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(
    readOnly = true,
    isolation = Isolation.REPEATABLE_READ,
    propagation = Propagation.REQUIRES_NEW)
public class ListQuestionPromptsService implements ListQuestionPromptsUseCase {
  private final CheckQuestionPetAccessPort access;
  private final LoadQuestionPromptHistoryPort history;

  public QuestionPromptsPage list(ListQuestionPromptsQuery q) {
    var mode = access.currentMode(q.memberId(), q.petId());
    var page = history.list(q.petId(), q.mode(), q.memberId(), q.page(), q.size());
    long count = page.totalElements();
    return new QuestionPromptsPage(
        page.items().stream().map(s -> QuestionPromptHistoryItem.from(s, mode)).toList(),
        q.page(),
        q.size(),
        count,
        count / q.size() + (count % q.size() == 0 ? 0 : 1));
  }
}
