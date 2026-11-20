package com.pawmento.question.application.port.in;

public record CreateQuestionAnswerCommand(
    Long memberId, Long petId, Long promptId, String content) {}
