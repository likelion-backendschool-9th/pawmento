package com.pawmento.question.adapter.in.web;

import com.pawmento.question.application.port.in.*;
import java.time.*;
import java.util.List;

record QuestionAnswerResponse(
    Long answerId,
    Long petId,
    Long promptId,
    String mode,
    Long questionId,
    String question,
    QuestionAnswerResult.Author author,
    String content,
    List<Attachment> attachments,
    Instant answeredAt,
    LocalDate answeredDate,
    Instant updatedAt,
    long version) {
  record Attachment(
      Long assetId,
      String type,
      String mimeType,
      long sizeBytes,
      Long durationMs,
      Integer width,
      Integer height,
      int sortOrder,
      boolean available,
      String contentUrl,
      String thumbnailUrl) {}

  static QuestionAnswerResponse from(QuestionAnswerResult a) {
    return new QuestionAnswerResponse(
        a.answerId(),
        a.petId(),
        a.promptId(),
        a.mode(),
        a.questionId(),
        a.question(),
        a.author(),
        a.content(),
        a.attachments().stream()
            .map(
                m -> {
                  String url =
                      "/api/v1/questions/answers/" + a.answerId() + "/attachments/" + m.assetId();
                  return new Attachment(
                      m.assetId(),
                      m.type(),
                      m.mimeType(),
                      m.sizeBytes(),
                      m.durationMs(),
                      m.width(),
                      m.height(),
                      m.sortOrder(),
                      m.available(),
                      m.available() ? url + "/content?petId=" + a.petId() : null,
                      m.available() && m.thumbnail()
                          ? url + "/thumbnail?petId=" + a.petId()
                          : null);
                })
            .toList(),
        a.answeredAt(),
        a.answeredDate(),
        a.updatedAt(),
        a.version());
  }

  record Created(
      QuestionAnswerResponse answer, boolean completedNow, boolean nextAssignmentRequired) {
    static Created from(CreateQuestionAnswerResult r) {
      return new Created(
          QuestionAnswerResponse.from(r.answer()), r.completedNow(), r.nextAssignmentRequired());
    }
  }

  record Page(
      List<QuestionAnswerResponse> items, int page, int size, long totalElements, long totalPages) {
    static Page from(QuestionAnswersPage r) {
      return new Page(
          r.items().stream().map(QuestionAnswerResponse::from).toList(),
          r.page(),
          r.size(),
          r.totalElements(),
          r.totalPages());
    }
  }
}
