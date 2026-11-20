package com.pawmento.question.application.port.in;

public record EditQuestionAnswerCommand(
    Long memberId, Long petId, Long answerId, String content, long version) {}
