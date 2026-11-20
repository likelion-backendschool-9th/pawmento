package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.QuestionAnswer;
import java.time.*;
import java.util.List;

public record QuestionAnswerResult(
    Long answerId,
    Long petId,
    Long promptId,
    String mode,
    Long questionId,
    String question,
    Author author,
    String content,
    List<Object> attachments,
    Instant answeredAt,
    LocalDate answeredDate,
    Instant updatedAt,
    long version) {
  public record Author(Long memberId, String displayName) {}

  public static QuestionAnswerResult from(QuestionAnswer a) {
    var p = a.getPrompt();
    return new QuestionAnswerResult(
        a.getId(),
        p.getPetId(),
        p.getId(),
        p.getMode().name(),
        p.getQuestionId(),
        p.getQuestionSnapshot(),
        new Author(a.getAuthorMemberId(), a.getAuthorNameSnapshot()),
        a.getContent(),
        List.of(),
        a.getAnsweredAt(),
        a.getAnsweredDate(),
        a.getUpdatedAt(),
        a.getVersion());
  }
}
