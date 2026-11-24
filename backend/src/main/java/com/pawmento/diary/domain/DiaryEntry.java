package com.pawmento.diary.domain;

import com.pawmento.diary.domain.exception.*;
import java.time.*;
import java.util.Objects;
import lombok.Getter;

@Getter
public final class DiaryEntry {
  private final Long id;
  private final Long petId;
  private final Long createdBy;
  private DiaryAuthorName authorName;
  private String content;
  private DiaryTitle title = DiaryTitle.empty();
  private LocalDate recordedDate;
  private final Instant createdAt;
  private Instant updatedAt;
  private final Long version;
  // null은 기존 행의 미이관 상태다. 명시적인 NONE으로 간주해 기존 핀을 지우지 않는다.
  private DiaryLocationIntent locationIntent;

  private DiaryEntry(
      Long id,
      Long petId,
      Long createdBy,
      String content,
      LocalDate recordedDate,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    if (petId == null
        || petId <= 0
        || createdBy == null
        || createdBy <= 0
        || content == null
        || content.strip().length() > 3000
        || recordedDate == null
        || createdAt == null
        || updatedAt == null
        || version == null
        || version < 0) throw new InvalidDiaryEntryException();
    this.id = id;
    this.petId = petId;
    this.createdBy = createdBy;
    this.content = content.strip();
    this.recordedDate = recordedDate;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  // 작성자를 정하기 전에 본문·날짜·첨부 규칙만 미리 검사한다. 일기 객체는 만들지 않는다.
  public static void validateDraft(
      Long petId,
      Long memberId,
      String content,
      LocalDate date,
      Instant now,
      boolean hasAttachments) {
    draft(petId, memberId, content, date, now, hasAttachments);
  }

  public static DiaryEntry create(
      Long petId,
      Long memberId,
      String content,
      LocalDate date,
      Instant now,
      boolean hasAttachments,
      DiaryAuthorName authorName) {
    var entry = draft(petId, memberId, content, date, now, hasAttachments);
    entry.authorName = Objects.requireNonNull(authorName);
    return entry;
  }

  private static DiaryEntry draft(
      Long petId,
      Long memberId,
      String content,
      LocalDate date,
      Instant now,
      boolean hasAttachments) {
    requireContent(content, hasAttachments);
    requireDate(date, now);
    var entry = new DiaryEntry(null, petId, memberId, content, date, now, now, 0L);
    entry.locationIntent = DiaryLocationIntent.none();
    return entry;
  }

  public static DiaryEntry restore(
      Long id,
      Long petId,
      Long author,
      String content,
      LocalDate date,
      Instant createdAt,
      Instant updatedAt,
      Long version,
      DiaryLocationIntent intent,
      DiaryAuthorName authorName,
      DiaryTitle title) {
    if (id == null || id <= 0) throw new InvalidDiaryEntryException();
    var entry = new DiaryEntry(id, petId, author, content, date, createdAt, updatedAt, version);
    entry.locationIntent = intent;
    entry.title = Objects.requireNonNull(title);
    entry.authorName = Objects.requireNonNull(authorName);
    return entry;
  }

  public void changeTitle(Long memberId, DiaryTitle title, Instant now) {
    requireAuthor(memberId);
    this.title = Objects.requireNonNull(title);
    this.updatedAt = Objects.requireNonNull(now);
  }

  public void initializeLocationIntent(DiaryLocationIntent intent) {
    if (locationIntent != null) throw new IllegalStateException("이미 초기화한 위치 원본입니다.");
    this.locationIntent = Objects.requireNonNull(intent);
  }

  public void changeLocation(Long memberId, DiaryLocationIntent intent, Instant now) {
    requireAuthor(memberId);
    Objects.requireNonNull(intent, "명시적인 위치 값이 필요합니다.");
    Objects.requireNonNull(now, "서버 시각이 필요합니다.");
    this.locationIntent = intent;
    this.updatedAt = now;
  }

  public void requireAuthor(Long memberId) {
    if (!Objects.equals(createdBy, memberId)) throw new DiaryAuthorDeniedException();
  }

  public void edit(Long memberId, String content, LocalDate date, Instant now) {
    if (content == null && date == null) throw new InvalidDiaryEntryException();
    edit(memberId, content, date, now, false);
  }

  public void edit(
      Long memberId, String content, LocalDate date, Instant now, boolean hasAttachments) {
    requireAuthor(memberId);
    String nextContent = content == null ? this.content : content.strip();
    LocalDate nextDate = date == null ? recordedDate : date;
    requireContent(nextContent, hasAttachments);
    requireDate(nextDate, now);
    this.content = nextContent;
    this.recordedDate = nextDate;
    this.updatedAt = now;
  }

  private static void requireContent(String content, boolean hasAttachments) {
    if (content == null
        || content.strip().length() > 3000
        || (content.isBlank() && !hasAttachments)) throw new InvalidDiaryEntryException();
  }

  private static void requireDate(LocalDate date, Instant now) {
    if (date == null
        || now == null
        || date.isAfter(now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()))
      throw new InvalidDiaryEntryException();
  }
}
