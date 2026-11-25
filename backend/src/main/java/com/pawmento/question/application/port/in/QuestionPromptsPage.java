package com.pawmento.question.application.port.in;

import java.util.List;

public record QuestionPromptsPage(
    List<QuestionPromptHistoryItem> items,
    int page,
    int size,
    long totalElements,
    long totalPages) {}
