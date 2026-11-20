package com.pawmento.question.application.port.in;

public record DeleteQuestionAnswerCommand(Long memberId, Long petId, Long answerId, long version) {}
