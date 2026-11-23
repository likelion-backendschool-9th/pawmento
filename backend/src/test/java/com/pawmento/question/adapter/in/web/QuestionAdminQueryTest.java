package com.pawmento.question.adapter.in.web;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.question.adapter.in.web.exception.InvalidQuestionRequestException;
import com.pawmento.question.domain.QuestionFilter;
import com.pawmento.question.domain.exception.InvalidQuestionFilterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionAdminQueryTest {
  @Test
  @DisplayName("#31-S23 검색어·페이지 경계 위반은 웹 전용 입력 예외로 거부한다")
  void webInputErrors() {
    for (String query : new String[]{"q=" + "x".repeat(301), "q=%00", "q=a%00b",
        "size=0", "size=101", "page=-1", "page=2147483648"}) {
      assertThatThrownBy(() -> QuestionAdminQuery.parse(1L, query))
          .as(query).isInstanceOf(InvalidQuestionRequestException.class);
    }
    assertThat(QuestionAdminQuery.parse(1L, "q=" + "x".repeat(300)).filter().search()).hasSize(300);
    assertThat(QuestionAdminQuery.parse(1L, "q=+++").filter().search()).isEmpty();
  }

  @Test
  @DisplayName("#31-S23 직접 호출도 널 문자·길이·페이지의 필터 불변조건을 유지한다")
  void directFilterInvariants() {
    assertThatThrownBy(() -> new QuestionFilter(null, null, "\0", 0, 20))
        .isInstanceOf(InvalidQuestionFilterException.class);
    assertThatThrownBy(() -> new QuestionFilter(null, null, "x".repeat(301), 0, 20))
        .isInstanceOf(InvalidQuestionFilterException.class);
    assertThatThrownBy(() -> new QuestionFilter(null, null, "", 0, 101))
        .isInstanceOf(InvalidQuestionFilterException.class);
  }
}
