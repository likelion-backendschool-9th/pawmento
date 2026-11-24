package com.pawmento.question.application.port.in;

public record GetQuestionPromptResult(
    QuestionPromptHistoryItem prompt, QuestionAnswersPage answers) {}
