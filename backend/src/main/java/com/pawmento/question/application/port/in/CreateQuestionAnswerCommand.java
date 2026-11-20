package com.pawmento.question.application.port.in;

import java.util.List;

public record CreateQuestionAnswerCommand(
    Long memberId, Long petId, Long promptId, String content, List<Long> attachmentIds) {
  public CreateQuestionAnswerCommand(Long memberId, Long petId, Long promptId, String content) {
    this(memberId, petId, promptId, content, null);
  }

  public CreateQuestionAnswerCommand {
    attachmentIds = QuestionAttachmentIds.copy(attachmentIds);
  }
}
