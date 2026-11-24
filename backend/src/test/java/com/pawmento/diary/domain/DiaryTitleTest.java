package com.pawmento.diary.domain;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiaryTitleTest {
  @Test
  @DisplayName("#32-S33/S35 기존 무제목을 복원하되 앞뒤 공백을 정리하고 코드 포인트 100자를 허용한다")
  void normalizesAndCountsCodePoints() {
    assertThat(new DiaryTitle(null).value()).isNull();
    assertThat(new DiaryTitle("  ").value()).isNull();
    assertThat(new DiaryTitle("").value()).isNull();
    assertThat(new DiaryTitle("  산책한 날  ").value()).isEqualTo("산책한 날");
    assertThat(new DiaryTitle("가".repeat(100)).value()).hasSize(100);
    assertThat(new DiaryTitle("🐾".repeat(100)).value().codePointCount(0, 200)).isEqualTo(100);
    assertThatThrownBy(() -> new DiaryTitle("가".repeat(101)))
        .isInstanceOf(InvalidDiaryTitleException.class);
    assertThatThrownBy(() -> new DiaryTitle("🐾".repeat(101)))
        .isInstanceOf(InvalidDiaryTitleException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\t",
        "앞\uD800뒤",
        "앞\uDC00뒤",
        "제목\n",
        "제\n목",
        "\r제목",
        "앞\u2028뒤",
        "앞\u2029뒤",
        "\u2028제목",
        "제목\u2029",
        "앞\u0000뒤"
      })
  @DisplayName("#32-S35 공백 정리 전에 Cc·줄 구분자·문단 구분자를 위치에 관계없이 거절한다")
  void rejectsControlsBeforeStripping(String value) {
    assertThatThrownBy(() -> new DiaryTitle(value)).isInstanceOf(InvalidDiaryTitleException.class);
  }

  @Test
  @DisplayName("#32-S33/S35 쓰기에는 제목이 필수이며 저장값은 정규화 없이 검증한다")
  void requiresTitleAndRestoresStrictly() {
    for (String value : new String[] {null, "", "   "})
      assertThatThrownBy(() -> new DiaryTitle(value).requirePresent())
          .isInstanceOf(InvalidDiaryTitleException.class);
    assertThat(DiaryTitle.restore(null).value()).isNull();
    assertThat(DiaryTitle.restore("산책").value()).isEqualTo("산책");
    for (String value : new String[] {"  산책  ", "", "   "})
      assertThatThrownBy(() -> DiaryTitle.restore(value))
          .isInstanceOf(InvalidDiaryTitleException.class);
  }
}
