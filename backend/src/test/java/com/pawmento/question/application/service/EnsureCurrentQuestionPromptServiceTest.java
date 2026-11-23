package com.pawmento.question.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pawmento.question.application.port.in.EnsureCurrentQuestionPromptCommand;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnsureCurrentQuestionPromptServiceTest {
  @Test
  @DisplayName("#31-S2 선택기가 같은 ID의 새 객체를 돌려줘도 원본 후보 문구·모드로 배정한다")
  void canonicalizesSelectedId() {
    var access = mock(CheckQuestionPetAccessPort.class);
    var store = mock(QuestionProgressStorePort.class);
    var questions = mock(LoadActiveQuestionsPort.class);
    var selection = mock(SelectQuestionPort.class);
    var now = Instant.parse("2026-11-20T00:00:00Z");
    var original = Question.restore(7L, QuestionMode.ACTIVE, "원본 질문", true, now, now, 0);
    var clone = Question.restore(7L, QuestionMode.GARDEN, "선택기에서 바꾼 문구", true, now, now, 0);
    when(access.currentMode(1L, 2L)).thenReturn(QuestionMode.ACTIVE);
    when(store.lock(2L, QuestionMode.ACTIVE))
        .thenReturn(QuestionProgress.create(2L, QuestionMode.ACTIVE));
    when(questions.loadByMode(QuestionMode.ACTIVE)).thenReturn(List.of(original));
    when(selection.select(anyList())).thenReturn(clone);
    when(store.savePrompt(any())).thenAnswer(invocation -> {
      QuestionPrompt assigned = invocation.getArgument(0);
      return QuestionPrompt.restore(10L, assigned.getPetId(), assigned.getMode(), assigned.getQuestionId(),
          assigned.getQuestionSnapshot(), assigned.getSequence(), assigned.getAssignedAt());
    });
    var service =
        new EnsureCurrentQuestionPromptService(
            access, store, questions, selection, Clock.fixed(now, ZoneOffset.UTC));
    var result = service.ensure(new EnsureCurrentQuestionPromptCommand(1L, 2L));
    assertThat(result.created()).isTrue();
    assertThat(result.current().mode()).isEqualTo("ACTIVE");
    assertThat(result.current().prompt().question()).isEqualTo("원본 질문");
    assertThat(result.current().prompt().questionId()).isEqualTo(7L);
    verify(store).saveProgress(any());
  }
}
