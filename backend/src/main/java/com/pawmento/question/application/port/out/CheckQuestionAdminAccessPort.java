package com.pawmento.question.application.port.out;

public interface CheckQuestionAdminAccessPort {
  void requireAdmin(Long memberId);
}
