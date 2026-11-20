package com.pawmento.question.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawmento.question.domain.exception.InvalidQuestionException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionTest {
  private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

  @Test
  @DisplayName("#31-S21 질문은 모드를 분리하고 본문을 정리하며 활성·version 0으로 생성한다")
  void createsQuestion() {
    for (var mode : QuestionMode.values()) {
      var question = Question.create(mode, "  우리 아이의 하루는?  ", NOW);
      assertThat(question.getId()).isNull();
      assertThat(question.getMode()).isEqualTo(mode);
      assertThat(question.getContent()).isEqualTo("우리 아이의 하루는?");
      assertThat(question.isEnabled()).isTrue();
      assertThat(question.getVersion()).isZero();
      assertThat(question.getCreatedAt()).isEqualTo(NOW);
      assertThat(question.getUpdatedAt()).isEqualTo(NOW);
    }
    assertThat(Question.create(QuestionMode.ACTIVE, "가".repeat(300), NOW).getContent())
        .hasSize(300);
    assertThat(Question.create(QuestionMode.GARDEN, "가", NOW).getContent()).hasSize(1);
  }

  @Test
  @DisplayName("#31-S21 모드·본문·시각·버전 불변식을 검증한다")
  void rejectsInvalidQuestion() {
    for (String content : new String[] {null, "", "  ", "가".repeat(301)}) {
      assertThatThrownBy(() -> Question.create(QuestionMode.ACTIVE, content, NOW))
          .isInstanceOf(InvalidQuestionException.class);
    }
    assertThatThrownBy(() -> Question.create(null, "질문", NOW))
        .isInstanceOf(InvalidQuestionException.class);
    assertThatThrownBy(() -> Question.create(QuestionMode.ACTIVE, "질문", null))
        .isInstanceOf(InvalidQuestionException.class);
    assertThatThrownBy(() -> Question.restore(1L, QuestionMode.ACTIVE, "질문", false, NOW, NOW, -1))
        .isInstanceOf(InvalidQuestionException.class);
    assertThatThrownBy(
            () ->
                Question.restore(1L, QuestionMode.ACTIVE, "질문", true, NOW, NOW.minusSeconds(1), 0))
        .isInstanceOf(InvalidQuestionException.class);
    for (Long id : new Long[] {null, 0L, -1L}) {
      assertThatThrownBy(() -> Question.restore(id, QuestionMode.ACTIVE, "질문", true, NOW, NOW, 0))
          .isInstanceOf(InvalidQuestionException.class);
    }
    assertThat(
            Question.restore(1L, QuestionMode.GARDEN, "질문", false, NOW, NOW.plusSeconds(1), 3)
                .isEnabled())
        .isFalse();
  }
}
