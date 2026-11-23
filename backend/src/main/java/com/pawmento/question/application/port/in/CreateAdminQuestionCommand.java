package com.pawmento.question.application.port.in;

public record CreateAdminQuestionCommand(Long memberId, String mode, String content) {}
