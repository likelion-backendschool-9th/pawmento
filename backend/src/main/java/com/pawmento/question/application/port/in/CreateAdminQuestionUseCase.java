package com.pawmento.question.application.port.in;

public interface CreateAdminQuestionUseCase {
  AdminQuestionResult create(CreateAdminQuestionCommand command);
}
