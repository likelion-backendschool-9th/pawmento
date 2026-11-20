package com.pawmento.question.application.port.in;

public record CreateQuestionAnswerResult(
    QuestionAnswerResult answer, boolean completedNow, boolean nextAssignmentRequired) {}
