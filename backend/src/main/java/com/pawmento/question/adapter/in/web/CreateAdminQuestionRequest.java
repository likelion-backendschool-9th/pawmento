package com.pawmento.question.adapter.in.web;

import com.pawmento.question.adapter.in.web.exception.InvalidQuestionRequestException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

record CreateAdminQuestionRequest(String mode, String content) {
  static CreateAdminQuestionRequest parse(JsonNode body) {
    if (body == null || !body.isObject()) throw new InvalidQuestionRequestException();
    for (String name : body.propertyNames()) {
      if (!Set.of("mode", "content").contains(name)) throw new InvalidQuestionRequestException();
    }
    var mode = body.get("mode");
    var content = body.get("content");
    if (mode == null || !mode.isString()
        || !Set.of("ACTIVE", "GARDEN").contains(mode.stringValue())
        || content == null || !content.isString()) throw new InvalidQuestionRequestException();
    return new CreateAdminQuestionRequest(mode.stringValue(), content.stringValue());
  }
}
