package com.pawmento.question.application.port.in;

import java.util.List;

public record AdminQuestionsPage(List<AdminQuestionResult> items, int page, int size,
    long totalElements, long totalPages) {
  public AdminQuestionsPage {
    items = List.copyOf(items);
  }
}
