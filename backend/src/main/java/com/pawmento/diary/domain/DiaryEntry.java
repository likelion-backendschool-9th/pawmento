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
  private String content;
  private LocalDate recordedDate;
  private final Instant createdAt;
  private Instant updatedAt;
  private final Long version;

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

  public static DiaryEntry create(
      Long petId, Long memberId, String content, LocalDate date, Instant now) {
    return create(petId, memberId, content, date, now, false);
  }

  public static DiaryEntry create(
      Long petId,
      Long memberId,
      String content,
      LocalDate date,
      Instant now,
      boolean hasAttachments) {
    requireContent(content, hasAttachments);
    requireDate(date, now);
    return new DiaryEntry(null, petId, memberId, content, date, now, now, 0L);
  }

  public static DiaryEntry restore(
      Long id,
      Long petId,
      Long author,
      String content,
      LocalDate date,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    if (id == null || id <= 0) throw new InvalidDiaryEntryException();
    return new DiaryEntry(id, petId, author, content, date, createdAt, updatedAt, version);
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
