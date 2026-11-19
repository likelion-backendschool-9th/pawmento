package com.pawmento.diary.domain;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.diary.domain.exception.InvalidDiaryQueryException;
import java.time.LocalDate;
import org.junit.jupiter.api.*;

class DiaryListFilterTest {
  private final LocalDate first = LocalDate.of(2024, 2, 28), last = LocalDate.of(2024, 3, 1);

  @Test
  @DisplayName("#32-S7 조회 기간·페이지의 불변 조건은 HTTP를 거치지 않아도 검사한다")
  void invalidFilters() {
    assertThatThrownBy(() -> new DiaryListFilter(first, first, null, 0, 20))
        .isInstanceOf(InvalidDiaryQueryException.class);
    assertThatThrownBy(() -> new DiaryListFilter(first, null, last, 0, 20))
        .isInstanceOf(InvalidDiaryQueryException.class);
    assertThatThrownBy(() -> new DiaryListFilter(null, last, first, 0, 20))
        .isInstanceOf(InvalidDiaryQueryException.class);
    assertThatThrownBy(() -> new DiaryListFilter(null, null, null, -1, 20))
        .isInstanceOf(InvalidDiaryQueryException.class);
    for (int size : new int[] {0, 101})
      assertThatThrownBy(() -> new DiaryListFilter(null, null, null, 0, size))
          .isInstanceOf(InvalidDiaryQueryException.class);
  }

  @Test
  @DisplayName("#32-S7 단일 날짜·한쪽 기간을 보존하고 큰 페이지의 오프셋은 Long으로 계산한다")
  void validFilters() {
    var exact = new DiaryListFilter(first, null, null, 0, 20);
    assertThat(exact.fromDate()).isEqualTo(first);
    assertThat(exact.toDate()).isEqualTo(first);
    assertThat(new DiaryListFilter(null, first, null, 0, 20).toDate()).isNull();
    assertThat(new DiaryListFilter(null, null, last, 0, 20).fromDate()).isNull();
    assertThat(new DiaryListFilter(null, first, first, 0, 20).toDate()).isEqualTo(first);
    assertThat(new DiaryListFilter(null, null, null, Integer.MAX_VALUE, 100).offset())
        .isEqualTo(214748364700L);
  }
}
