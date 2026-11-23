package com.pawmento.question.application.port.in;

public interface GetAdminQuestionUseCase {
  AdminQuestionResult get(GetAdminQuestionQuery query);
}
