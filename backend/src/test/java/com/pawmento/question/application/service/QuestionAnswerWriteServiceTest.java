package com.pawmento.question.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;

class QuestionAnswerWriteServiceTest {
  @Test
  @DisplayName("#31-S6/S9 권한 포트 이후의 과거 회차 추가 답변은 다른 작성자로 저장하되 현재 진행을 다시 바꾸지 않는다")
  void pastAnswerDoesNotAdvance() {
    // Application contract only. Real family membership remains a separate integration task.
    var access = mock(CheckQuestionPetWriteAccessPort.class);
    var names = mock(LoadQuestionAuthorNamePort.class);
    var answers = mock(QuestionAnswerStorePort.class);
    var progress = mock(QuestionProgressStorePort.class);
    var prompts = mock(QuestionPromptCompletionPort.class);
    var now = Instant.parse("2026-11-20T15:00:00Z");
    var old =
        QuestionPrompt.restore(
            10L,
            2L,
            QuestionMode.ACTIVE,
            3L,
            "그날의 질문",
            1,
            now.minusSeconds(86400),
            now.minusSeconds(3600));
    var current = QuestionProgress.restore(2L, QuestionMode.ACTIVE, 11L, 3);
    when(access.currentModeForWrite(5L, 2L)).thenReturn(QuestionMode.ACTIVE);
    when(names.loadName(5L)).thenReturn("두 번째 작성자");
    when(progress.lock(2L, QuestionMode.ACTIVE)).thenReturn(current);
    when(prompts.find(2L, 10L)).thenReturn(Optional.of(old));
    when(answers.save(any()))
        .thenAnswer(
            inv -> {
              QuestionAnswer a = inv.getArgument(0);
              return QuestionAnswer.restore(
                  12L,
                  a.getPrompt(),
                  a.getAuthorMemberId(),
                  a.getAuthorNameSnapshot(),
                  a.getContent(),
                  a.getAnsweredAt(),
                  a.getAnsweredDate(),
                  a.getUpdatedAt(),
                  0);
            });
    var service =
        new QuestionAnswerWriteService(
            access, names, answers, progress, prompts, Clock.fixed(now, ZoneOffset.UTC));
    var result = service.create(new CreateQuestionAnswerCommand(5L, 2L, 10L, "두 번째 사람의 답변"));
    assertThat(result.completedNow()).isFalse();
    assertThat(result.nextAssignmentRequired()).isFalse();
    assertThat(result.answer().author().memberId()).isEqualTo(5);
    assertThat(current.getCurrentPromptId()).isEqualTo(11);
    verify(prompts, never()).complete(any());
    verify(progress, never()).saveProgress(any());
  }
}
