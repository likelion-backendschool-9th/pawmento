package com.pawmento.question.adapter.in.web;

import com.pawmento.question.application.port.in.AdminQuestionResult;
import com.pawmento.question.application.port.in.AdminQuestionsPage;
import java.time.Instant;
import java.util.List;

public record AdminQuestionResponse(Long questionId, String mode, String content, boolean enabled,
    Instant createdAt, Instant updatedAt, long version) {
  static AdminQuestionResponse from(AdminQuestionResult result) {
    return new AdminQuestionResponse(result.questionId(), result.mode(), result.content(),
        result.enabled(), result.createdAt(), result.updatedAt(), result.version());
  }

  public record Page(List<AdminQuestionResponse> items, int page, int size,
      long totalElements, long totalPages) {
    public Page {
      items = List.copyOf(items);
    }

    static Page from(AdminQuestionsPage result) {
      return new Page(result.items().stream().map(AdminQuestionResponse::from).toList(),
          result.page(), result.size(), result.totalElements(), result.totalPages());
    }
  }
}
