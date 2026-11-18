package com.pawmento.diary.domain;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.diary.domain.exception.*;
import java.time.*;
import org.junit.jupiter.api.*;

class DiaryEntryTest {
  private final Instant now = Instant.parse("2026-11-17T15:00:00Z");

  @Test
  @DisplayName("#32-S3 텍스트 경계와 서울 자정의 기록 날짜를 검사한다")
  void boundaries() {
    assertThat(
            DiaryEntry.create(1L, 2L, "가".repeat(3000), LocalDate.of(2026, 11, 18), now)
                .getContent())
        .hasSize(3000);
    for (String text : new String[] {" ", "가".repeat(3001)})
      assertThatThrownBy(() -> DiaryEntry.create(1L, 2L, text, LocalDate.of(2026, 11, 18), now))
          .isInstanceOf(InvalidDiaryEntryException.class);
    assertThatThrownBy(
            () -> DiaryEntry.create(1L, 2L, "일기", LocalDate.of(2026, 11, 18), now.minusSeconds(1)))
        .isInstanceOf(InvalidDiaryEntryException.class);
    assertThat(DiaryEntry.create(1L, 2L, "  기억  ", LocalDate.of(2000, 1, 1), now).getContent())
        .isEqualTo("기억");
  }

  @Test
  @DisplayName("#32-S1 수정은 원 작성자와 생성 시각을 유지하고 서버 수정 시각을 기록한다")
  void edit() {
    var entry = DiaryEntry.restore(3L, 1L, 2L, "기억", LocalDate.of(2026, 11, 17), now, now, 0L);
    entry.edit(2L, "산책", null, now.plusSeconds(2));
    assertThat(entry.getRecordedDate()).isEqualTo(LocalDate.of(2026, 11, 17));
    assertThat(entry.getCreatedAt()).isEqualTo(now);
    assertThat(entry.getUpdatedAt()).isEqualTo(now.plusSeconds(2));
    assertThat(entry.getCreatedBy()).isEqualTo(2L);
  }

  @Test
  @DisplayName("#32-S2 다른 작성자는 일기를 변경할 수 없다")
  void author() {
    var entry = DiaryEntry.create(1L, 2L, "기억", LocalDate.of(2026, 11, 17), now);
    assertThatThrownBy(() -> entry.edit(3L, "변경", null, now))
        .isInstanceOf(DiaryAuthorDeniedException.class);
    assertThat(entry.getContent()).isEqualTo("기억");
  }
}
