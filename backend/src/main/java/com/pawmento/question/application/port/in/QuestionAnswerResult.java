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
    List<Attachment> attachments,
    Instant answeredAt,
    LocalDate answeredDate,
    Instant updatedAt,
    long version) {
  public record Author(Long memberId, String displayName) {}

  public record Attachment(
      Long assetId,
      String type,
      String mimeType,
      long sizeBytes,
      Long durationMs,
      Integer width,
      Integer height,
      int sortOrder,
      boolean available,
      boolean thumbnail,
      String filename) {}

  public static QuestionAnswerResult from(QuestionAnswer a) {
    return from(a, List.of());
  }

  public static QuestionAnswerResult from(QuestionAnswer a, List<QuestionAttachment> assets) {
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
        java.util.stream.IntStream.range(0, assets.size())
            .mapToObj(
                i -> {
                  var m = assets.get(i);
                  return new Attachment(
                      m.assetId(),
                      m.type(),
                      m.mimeType(),
                      m.sizeBytes(),
                      m.durationMs(),
                      m.width(),
                      m.height(),
                      i,
                      m.available(),
                      m.thumbnail(),
                      m.filename());
                })
            .toList(),
        a.getAnsweredAt(),
        a.getAnsweredDate(),
        a.getUpdatedAt(),
        a.getVersion());
  }
}
