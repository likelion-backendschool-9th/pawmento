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
  @DisplayName("#31-S27 질문 원본·배정·완료·답변의 새 시각은 같은 마이크로초 정밀도를 사용한다")
  void questionWritesShareTimePrecision() {
    var raw = Instant.parse("2026-11-23T14:59:59.999999999Z");
    var time = Instant.parse("2026-11-23T14:59:59.999999Z");
    var created = Question.create(QuestionMode.ACTIVE, "원본", raw);
    assertThat(created.getCreatedAt()).isEqualTo(time);
    assertThat(created.getUpdatedAt()).isEqualTo(time);
    var stored = Question.restore(1L, created.getMode(), created.getContent(), true,
        created.getCreatedAt(), created.getUpdatedAt(), 0);
    var assigned = QuestionPrompt.assign(2L, QuestionMode.ACTIVE, stored, 1, raw);
    assertThat(assigned.getAssignedAt()).isEqualTo(time);
    var prompt = QuestionPrompt.restore(3L, 2L, QuestionMode.ACTIVE, 1L, "원본", 1, assigned.getAssignedAt());
    var answer = QuestionAnswer.create(prompt, 4L, "보호자", "답변", raw);
    assertThat(answer.getAnsweredAt()).isEqualTo(time);
    assertThat(answer.getUpdatedAt()).isEqualTo(time);
    assertThat(answer.getAnsweredDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 23));
    prompt.complete(raw);
    assertThat(prompt.getCompletedAt()).isEqualTo(time);
    var later = raw.plusSeconds(1);
    assertThat(stored.edit("변경", null, later).getUpdatedAt()).isEqualTo(time.plusSeconds(1));
    answer.edit("수정", later);
    assertThat(answer.getUpdatedAt()).isEqualTo(time.plusSeconds(1));
    assertThat(answer.getAnsweredAt()).isEqualTo(time);
  }

  @Test
  @DisplayName("#31-S27 이미 저장된 원본·배정·답변 복원에서는 전달받은 시각을 다시 정규화하지 않는다")
  void restorationPreservesExistingTimestamps() {
    var storedTime = NOW.plusNanos(123456789);
    var original = Question.restore(1L, QuestionMode.GARDEN, "기억", true, storedTime, storedTime, 1);
    var prompt = QuestionPrompt.restore(2L, 3L, QuestionMode.GARDEN, 1L, "기억", 1, storedTime, storedTime);
    var answer = QuestionAnswer.restore(4L, prompt, 5L, "보호자", "답변", storedTime,
        storedTime.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate(), storedTime, 1);
    assertThat(original.getCreatedAt()).isEqualTo(storedTime);
    assertThat(original.getUpdatedAt()).isEqualTo(storedTime);
    assertThat(prompt.getAssignedAt()).isEqualTo(storedTime);
    assertThat(prompt.getCompletedAt()).isEqualTo(storedTime);
    assertThat(answer.getAnsweredAt()).isEqualTo(storedTime);
    assertThat(answer.getUpdatedAt()).isEqualTo(storedTime);
  }

  @Test
  @DisplayName("#31-S25 동일 값 수정은 Clock이 생성 시각보다 이전이어도 기존 원본을 그대로 반환한다")
  void noOpDoesNotValidateReplacementTime() {
    var question = Question.restore(1L, QuestionMode.ACTIVE, "기존 질문", true, NOW, NOW, 0);
    assertThat(question.edit(" 기존 질문 ", true, NOW.minusNanos(1))).isSameAs(question);
    assertThatThrownBy(() -> question.edit(" ", true, NOW.minusNanos(1)))
        .isInstanceOf(InvalidQuestionException.class);
  }

  @Test
  @DisplayName("#31-S25 질문 수정은 불변 필드·생략 필드를 보존하고 동일 값이면 같은 원본을 반환한다")
  void editsQuestion() {
    var original = Question.restore(1L, QuestionMode.GARDEN, "추억 질문", true, NOW, NOW, 3);
    var changed = original.edit("  새로운 추억  ", null, NOW.plusSeconds(1));
    assertThat(changed.getContent()).isEqualTo("새로운 추억");
    assertThat(changed.isEnabled()).isTrue();
    assertThat(changed.getId()).isEqualTo(1L);
    assertThat(changed.getMode()).isEqualTo(QuestionMode.GARDEN);
    assertThat(changed.getCreatedAt()).isEqualTo(NOW);
    assertThat(changed.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    // 버전 증가는 영속성 @Version의 책임이다.
    assertThat(changed.getVersion()).isEqualTo(3);
    assertThat(original.edit("  추억 질문 ", true, NOW.plusSeconds(1))).isSameAs(original);
    assertThat(original.edit(null, false, NOW.plusSeconds(1)).isEnabled()).isFalse();
    assertThat(original.isEnabled()).isTrue();
    for (String value : new String[]{"", " ", "가".repeat(301), "잘못된\0본문"})
      assertThatThrownBy(() -> original.edit(value, null, NOW.plusSeconds(1)))
          .isInstanceOf(InvalidQuestionException.class);
    assertThatThrownBy(() -> original.edit(null, null, NOW)).isInstanceOf(InvalidQuestionException.class);
  }

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
