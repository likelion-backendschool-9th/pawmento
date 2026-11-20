package com.pawmento.question.application.port.in;

public record EnsureCurrentQuestionPromptResult(
    boolean created, CurrentQuestionPromptResult current) {}
