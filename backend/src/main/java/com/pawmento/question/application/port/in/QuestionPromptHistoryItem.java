package com.pawmento.question.application.port.in;

import com.pawmento.question.application.port.out.LoadQuestionPromptHistoryPort;
import com.pawmento.question.domain.QuestionMode;
import java.time.Instant;

public record QuestionPromptHistoryItem(
    Long promptId,
    Long petId,
    String mode,
    Long questionId,
    String question,
    long sequence,
    Instant assignedAt,
    Instant completedAt,
    long answerCount,
    Long myAnswerId,
    boolean canAnswer) {
  public static QuestionPromptHistoryItem from(
      LoadQuestionPromptHistoryPort.Summary summary, QuestionMode currentMode) {
    var p = summary.prompt();
    return new QuestionPromptHistoryItem(
        p.getId(),
        p.getPetId(),
        p.getMode().name(),
        p.getQuestionId(),
        p.getQuestionSnapshot(),
        p.getSequence(),
        p.getAssignedAt(),
        p.getCompletedAt(),
        summary.answerCount(),
        summary.myAnswerId(),
        p.getMode() == currentMode && summary.myAnswerId() == null);
  }
}
