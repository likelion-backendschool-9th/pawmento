package com.pawmento.question.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionProgressTest {
  private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

  Question question(long id, QuestionMode mode, boolean enabled) {
    return Question.restore(id, mode, "질문 " + id, enabled, NOW, NOW, 0);
  }

  @Test
  @DisplayName("#31-S2/S10 같은 모드의 활성 후보에서 직전 원본을 피하되 하나뿐이면 반복을 허용한다")
  void eligibleCandidates() {
    var progress = QuestionProgress.create(1L, QuestionMode.ACTIVE);
    var first = question(1, QuestionMode.ACTIVE, true);
    var second = question(2, QuestionMode.ACTIVE, true);
    var wrong = question(3, QuestionMode.GARDEN, true);
    var disabled = question(4, QuestionMode.ACTIVE, false);
    assertThat(progress.candidates(List.of(first, second, wrong, disabled), 1L))
        .containsExactly(second);
    assertThat(progress.candidates(List.of(first, wrong, disabled), 1L)).containsExactly(first);
  }

  @Test
  @DisplayName("#31-S3 진행은 배정 후 포인터와 다음 순번을 함께 바꾸고 중복 배정을 막는다")
  void assignsOnce() {
    var progress = QuestionProgress.create(1L, QuestionMode.ACTIVE);
    assertThat(progress.getNextSequence()).isEqualTo(1);
    progress.assigned(10L);
    assertThat(progress.getCurrentPromptId()).isEqualTo(10);
    assertThat(progress.getNextSequence()).isEqualTo(2);
    assertThatThrownBy(() -> progress.assigned(11L)).isInstanceOf(IllegalStateException.class);
    assertThat(progress.getCurrentPromptId()).isEqualTo(10);
    assertThat(progress.getNextSequence()).isEqualTo(2);
  }

  @Test
  @DisplayName("#31-S2/S10 배정 문구를 복사하고 다른 모드·비활성 질문을 거절한다")
  void snapshotsQuestion() {
    var prompt =
        QuestionPrompt.assign(
            1L, QuestionMode.ACTIVE, question(1, QuestionMode.ACTIVE, true), 1, NOW);
    assertThat(prompt.getQuestionSnapshot()).isEqualTo("질문 1");
    assertThat(prompt.getAssignedAt()).isEqualTo(NOW);
    assertThatThrownBy(
            () ->
                QuestionPrompt.assign(
                    1L, QuestionMode.ACTIVE, question(1, QuestionMode.GARDEN, true), 1, NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                QuestionPrompt.assign(
                    1L, QuestionMode.ACTIVE, question(1, QuestionMode.ACTIVE, false), 1, NOW))
        .isInstanceOf(IllegalStateException.class);
    var progress = QuestionProgress.restore(1L, QuestionMode.ACTIVE, 10L, 2);
    progress.requireCurrent(QuestionPrompt.restore(10L, 1L, QuestionMode.ACTIVE, 1L, "질문", 1, NOW));
    assertThatThrownBy(
            () ->
                progress.requireCurrent(
                    QuestionPrompt.restore(10L, 2L, QuestionMode.ACTIVE, 1L, "질문", 1, NOW)))
        .isInstanceOf(
            com.pawmento.question.domain.exception.QuestionProgressInconsistentException.class);
  }
}
