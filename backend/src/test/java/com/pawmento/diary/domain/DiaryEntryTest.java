package com.pawmento.diary.domain;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.diary.domain.exception.*;
import java.time.*;
import org.junit.jupiter.api.*;

class DiaryEntryTest {
  private final Instant now = Instant.parse("2026-11-17T15:00:00Z");
  private final DiaryAuthorName author = new DiaryAuthorName("작성자");

  private DiaryEntry create(String content, LocalDate date, Instant at) {
    return DiaryEntry.create(1L, 2L, content, date, at, false, author);
  }

  private DiaryEntry restore(String content) {
    return DiaryEntry.restore(
        3L,
        1L,
        2L,
        content,
        LocalDate.of(2026, 11, 17),
        now,
        now,
        0L,
        null,
        author,
        DiaryTitle.empty());
  }

  @Test
  @DisplayName("#32-S3 텍스트 경계와 서울 자정의 기록 날짜를 검사한다")
  void boundaries() {
    assertThat(create("가".repeat(3000), LocalDate.of(2026, 11, 18), now).getContent())
        .hasSize(3000);
    for (String text : new String[] {" ", "가".repeat(3001)})
      assertThatThrownBy(() -> create(text, LocalDate.of(2026, 11, 18), now))
          .isInstanceOf(InvalidDiaryEntryException.class);
    assertThatThrownBy(() -> create("일기", LocalDate.of(2026, 11, 18), now.minusSeconds(1)))
        .isInstanceOf(InvalidDiaryEntryException.class);
    assertThat(create("  기억  ", LocalDate.of(2000, 1, 1), now).getContent()).isEqualTo("기억");
  }

  @Test
  @DisplayName("#32-S1 수정은 원 작성자와 생성 시각을 유지하고 서버 수정 시각을 기록한다")
  void edit() {
    var entry = restore("기억");
    entry.edit(2L, "산책", null, now.plusSeconds(2));
    assertThat(entry.getRecordedDate()).isEqualTo(LocalDate.of(2026, 11, 17));
    assertThat(entry.getCreatedAt()).isEqualTo(now);
    assertThat(entry.getUpdatedAt()).isEqualTo(now.plusSeconds(2));
    assertThat(entry.getCreatedBy()).isEqualTo(2L);
  }

  @Test
  @DisplayName("#32-S2 다른 작성자는 일기를 변경할 수 없다")
  void author() {
    var entry = create("기억", LocalDate.of(2026, 11, 17), now);
    assertThatThrownBy(() -> entry.edit(3L, "변경", null, now))
        .isInstanceOf(DiaryAuthorDeniedException.class);
    assertThat(entry.getContent()).isEqualTo("기억");
  }

  @Test
  @DisplayName("#44-S4 새 일기의 NONE과 기존 미이관 상태를 구분하며 작성자만 위치를 변경한다")
  void locationIntent() {
    var entry = create("일기", LocalDate.of(2026, 11, 17), now);
    assertThat(entry.getLocationIntent()).isEqualTo(DiaryLocationIntent.none());
    var legacy = restore("일기");
    assertThat(legacy.getLocationIntent()).isNull();
    var manual = DiaryLocationIntent.manual(37.0, 127.0, "공원");
    assertThatThrownBy(() -> entry.changeLocation(3L, manual, now.plusSeconds(1)))
        .isInstanceOf(DiaryAuthorDeniedException.class);
    assertThat(entry.getLocationIntent()).isEqualTo(DiaryLocationIntent.none());
    entry.changeLocation(2L, manual, now.plusSeconds(1));
    assertThat(entry.getLocationIntent()).isEqualTo(manual);
    assertThat(entry.getCreatedAt()).isEqualTo(now);
    assertThat(entry.getUpdatedAt()).isEqualTo(now.plusSeconds(1));
  }
}
