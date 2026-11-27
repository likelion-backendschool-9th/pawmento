package com.pawmento.question.domain;

import com.pawmento.question.domain.exception.*;
import java.time.*;
import lombok.Getter;

@Getter
public final class QuestionAnswer {
  public static final int CONTENT_MAX_LENGTH = 3000;
  private final Long id;
  private final QuestionPrompt prompt;
  private final Long authorMemberId;
  private final String authorNameSnapshot;
  private String content;
  private final Instant answeredAt;
  private final LocalDate answeredDate;
  private Instant updatedAt;
  private final long version;
  private boolean edited;

  private QuestionAnswer(
      Long id,
      QuestionPrompt prompt,
      Long author,
      String name,
      String content,
      Instant at,
      LocalDate date,
      Instant updated,
      long version) {
    if (prompt == null
        || prompt.getId() == null
        || author == null
        || author <= 0
        || name == null
        || name.isBlank()
        || at == null
        || date == null
        || updated == null
        || version < 0
        || !date.equals(at.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()))
      throw new InvalidQuestionAnswerException();
    this.id = id;
    this.prompt = prompt;
    this.authorMemberId = author;
    this.authorNameSnapshot = name;
    this.content = validate(content);
    this.answeredAt = at;
    this.answeredDate = date;
    this.updatedAt = updated;
    this.version = version;
  }

  public static QuestionAnswer create(
      QuestionPrompt prompt, Long author, String name, String content, Instant now) {
    if (now == null) throw new InvalidQuestionAnswerException();
    now = QuestionTime.forWrite(now);
    return new QuestionAnswer(
        null,
        prompt,
        author,
        name,
        content,
        now,
        now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
        now,
        0);
  }

  public static QuestionAnswer restore(
      Long id,
      QuestionPrompt prompt,
      Long author,
      String name,
      String content,
      Instant at,
      LocalDate date,
      Instant updated,
      long version) {
    if (id == null || id <= 0) throw new InvalidQuestionAnswerException();
    return new QuestionAnswer(id, prompt, author, name, content, at, date, updated, version);
  }

  public void requireEditable(Long member, QuestionMode currentMode, long expectedVersion) {
    if (!authorMemberId.equals(member)) throw new QuestionAnswerForbiddenException();
    requireCurrent(currentMode, expectedVersion);
  }

  public void requireDeletable(
      Long member, boolean owner, QuestionMode currentMode, long expectedVersion) {
    if (!owner && !authorMemberId.equals(member)) throw new QuestionAnswerForbiddenException();
    requireCurrent(currentMode, expectedVersion);
  }

  // 수정·삭제가 같은 충돌 계약(409)을 쓰도록 모드와 버전 판정을 한곳에 둔다.
  private void requireCurrent(QuestionMode currentMode, long expectedVersion) {
    if (currentMode != prompt.getMode() || version != expectedVersion)
      throw new QuestionAnswerConflictException();
  }

  public void edit(String content, Instant now) {
    if (now == null) throw new InvalidQuestionAnswerException();
    this.content = validate(content);
    this.updatedAt = QuestionTime.forWrite(now);
    this.edited = true;
  }

  private static String validate(String content) {
    if (content == null
        || content.strip().isEmpty()
        || content.strip().length() > CONTENT_MAX_LENGTH)
      throw new InvalidQuestionAnswerException();
    return content.strip();
  }
}
