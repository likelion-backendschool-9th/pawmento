package com.pawmento.question.application.port.in;

import java.util.List;

public record EditQuestionAnswerCommand(
    Long memberId,
    Long petId,
    Long answerId,
    String content,
    long version,
    List<Long> attachmentIds) {
  public EditQuestionAnswerCommand(
      Long memberId, Long petId, Long answerId, String content, long version) {
    this(memberId, petId, answerId, content, version, null);
  }

  public EditQuestionAnswerCommand {
    attachmentIds = QuestionAttachmentIds.copy(attachmentIds);
  }
}
