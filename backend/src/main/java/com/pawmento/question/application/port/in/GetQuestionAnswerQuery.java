package com.pawmento.question.application.port.in;

public record GetQuestionAnswerQuery(Long memberId, Long petId, Long answerId) {}
