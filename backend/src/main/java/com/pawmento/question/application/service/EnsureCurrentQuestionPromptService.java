package com.pawmento.question.application.service;

import com.pawmento.question.application.exception.QuestionPoolEmptyException;
import com.pawmento.question.application.port.in.EnsureCurrentQuestionPromptCommand;
import com.pawmento.question.application.port.in.EnsureCurrentQuestionPromptResult;
import com.pawmento.question.application.port.in.EnsureCurrentQuestionPromptUseCase;
import com.pawmento.question.application.port.out.CheckQuestionPetAccessPort;
import com.pawmento.question.application.port.out.LoadActiveQuestionsPort;
import com.pawmento.question.application.port.out.QuestionProgressStorePort;
import com.pawmento.question.application.port.out.SelectQuestionPort;
import com.pawmento.question.domain.QuestionPrompt;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnsureCurrentQuestionPromptService implements EnsureCurrentQuestionPromptUseCase {
  private final CheckQuestionPetAccessPort access;
  private final QuestionProgressStorePort store;
  private final LoadActiveQuestionsPort questions;
  private final SelectQuestionPort selection;
  private final Clock clock;

  @Override
  @Transactional
  public EnsureCurrentQuestionPromptResult ensure(EnsureCurrentQuestionPromptCommand command) {
    var mode = access.currentMode(command.memberId(), command.petId());
    var progress = store.lock(command.petId(), mode);
    if (progress.getCurrentPromptId() != null) {
      var current = store.loadPrompt(progress.getCurrentPromptId());
      progress.requireCurrent(current);
      return new EnsureCurrentQuestionPromptResult(
          false, CurrentQuestionPromptValues.result(command.petId(), mode, current));
    }
    var candidates =
        progress.candidates(
            questions.loadByMode(mode), store.lastQuestionId(command.petId(), mode));
    if (candidates.isEmpty()) throw new QuestionPoolEmptyException();
    var selected = selection.select(candidates);
    var chosen =
        candidates.stream()
            .filter(candidate -> selected != null && candidate.getId().equals(selected.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("선택기가 질문 풀 밖의 질문을 반환했습니다."));
    var prompt =
        QuestionPrompt.assign(
            command.petId(), mode, chosen, progress.getNextSequence(), clock.instant());
    var saved = store.savePrompt(prompt);
    progress.assigned(saved.getId());
    store.saveProgress(progress);
    return new EnsureCurrentQuestionPromptResult(
        true, CurrentQuestionPromptValues.result(command.petId(), mode, saved));
  }
}
