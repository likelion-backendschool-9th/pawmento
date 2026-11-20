package com.pawmento.question.application.port.in;

import java.util.List;

public record QuestionAnswersPage(
    List<QuestionAnswerResult> items, int page, int size, long totalElements, long totalPages) {}
