package com.pawmento.question.adapter.in.web;

import com.pawmento.question.adapter.in.web.exception.InvalidQuestionRequestException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

record QuestionAnswerBody(String content, long version) {
  static QuestionAnswerBody parse(JsonNode body, boolean editing) {
    if (body == null || !body.isObject()) throw new InvalidQuestionRequestException();
    Set<String> allowed =
        editing
            ? Set.of("content", "version", "attachmentIds")
            : Set.of("content", "attachmentIds");
    for (String key : body.propertyNames())
      if (!allowed.contains(key)) throw new InvalidQuestionRequestException();
    var content = body.get("content");
    if ((content == null && !editing) || (content != null && !content.isString()))
      throw new InvalidQuestionRequestException();
    var attachments = body.get("attachmentIds");
    // Text phase: explicitly reject attachments rather than silently dropping uploaded file IDs.
    if (attachments != null && (!attachments.isArray() || !attachments.isEmpty()))
      throw new InvalidQuestionRequestException();
    long version = 0;
    if (editing) {
      var value = body.get("version");
      if (value == null
          || !value.isIntegralNumber()
          || !value.canConvertToLong()
          || value.longValue() < 0) throw new InvalidQuestionRequestException();
      version = value.longValue();
    }
    return new QuestionAnswerBody(content == null ? null : content.stringValue(), version);
  }
}
