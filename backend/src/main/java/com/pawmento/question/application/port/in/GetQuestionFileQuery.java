package com.pawmento.question.application.port.in;

public record GetQuestionFileQuery(
    Long memberId, Long petId, Long answerId, Long assetId, boolean thumbnail) {}
