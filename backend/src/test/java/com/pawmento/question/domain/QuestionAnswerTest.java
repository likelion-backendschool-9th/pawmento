package com.pawmento.question.domain;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.question.domain.exception.*;
import java.time.*;
import org.junit.jupiter.api.*;

class QuestionAnswerTest {
  private final Instant at = Instant.parse("2026-11-20T15:00:00Z");

  private QuestionPrompt prompt() {
    return QuestionPrompt.restore(
        1L, 2L, QuestionMode.ACTIVE, 3L, "오늘의 질문", 1, at.minusSeconds(86400));
  }

  @Test
  @DisplayName("#31-S5/S13 답변은 서버 시각의 한국 날짜를 사용하고 수정해도 작성자·질문·등록일은 유지한다")
  void immutableIdentity() {
    var a = QuestionAnswer.create(prompt(), 4L, "작성 당시 이름", "  답변  ", at);
    a.edit("새 답변", at.plusSeconds(86400));
    assertThat(a.getAnsweredDate()).isEqualTo(LocalDate.of(2026, 11, 21));
    assertThat(a.getAnsweredAt()).isEqualTo(at);
    assertThat(a.getAuthorMemberId()).isEqualTo(4);
    assertThat(a.getAuthorNameSnapshot()).isEqualTo("작성 당시 이름");
    assertThat(a.getPrompt().getQuestionSnapshot()).isEqualTo("오늘의 질문");
    assertThat(a.getContent()).isEqualTo("새 답변");
  }

  @Test
  @DisplayName("#31-S8/S12/S14 본인만 같은 모드·버전으로 변경하고 잘못된 본문·등록일은 거절한다")
  void constraints() {
    var a = QuestionAnswer.create(prompt(), 4L, "이름", "가".repeat(3000), at);
    a.requireEditable(4L, QuestionMode.ACTIVE, 0);
    assertThatThrownBy(() -> a.requireEditable(5L, QuestionMode.ACTIVE, 0))
        .isInstanceOf(QuestionAnswerForbiddenException.class);
    assertThatThrownBy(() -> a.requireEditable(4L, QuestionMode.GARDEN, 0))
        .isInstanceOf(QuestionAnswerConflictException.class);
    assertThatThrownBy(() -> a.requireEditable(4L, QuestionMode.ACTIVE, 1))
        .isInstanceOf(QuestionAnswerConflictException.class);
    for (String content : new String[] {null, " ", "가".repeat(3001)})
      assertThatThrownBy(() -> QuestionAnswer.create(prompt(), 4L, "이름", content, at))
          .isInstanceOf(InvalidQuestionAnswerException.class);
    assertThatThrownBy(
            () ->
                QuestionAnswer.restore(
                    1L, prompt(), 4L, "이름", "답변", at, LocalDate.of(2026, 11, 20), at, 0))
        .isInstanceOf(InvalidQuestionAnswerException.class);
  }

  @Test
  @DisplayName("#31-S9 완료 시각은 한 번만 기록하고 현재 진행만 완료할 수 있다")
  void completion() {
    var prompt = prompt();
    prompt.complete(at);
    prompt.complete(at.plusSeconds(1));
    assertThat(prompt.getCompletedAt()).isEqualTo(at);
    var p = QuestionProgress.restore(2L, QuestionMode.ACTIVE, 1L, 2);
    p.completeCurrent(prompt);
    assertThat(p.getCurrentPromptId()).isNull();
    assertThat(p.getNextSequence()).isEqualTo(2);
    assertThatThrownBy(() -> p.completeCurrent(prompt))
        .isInstanceOf(QuestionProgressInconsistentException.class);
  }
}
