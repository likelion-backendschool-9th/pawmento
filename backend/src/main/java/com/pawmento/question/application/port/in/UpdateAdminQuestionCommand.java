package com.pawmento.question.application.port.in;

public record UpdateAdminQuestionCommand(Long memberId, Long questionId, String content,
    Boolean enabled, long version) {}
