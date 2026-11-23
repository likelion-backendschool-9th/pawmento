package com.pawmento.question.adapter.in.web;

import com.pawmento.question.adapter.in.web.exception.InvalidQuestionRequestException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

record UpdateAdminQuestionRequest(String content, Boolean enabled, long version) {
  static UpdateAdminQuestionRequest parse(JsonNode body) {
    if (body == null || !body.isObject()) throw new InvalidQuestionRequestException();
    for (String name : body.propertyNames()) {
      if (!Set.of("content", "enabled", "version").contains(name))
        throw new InvalidQuestionRequestException();
    }
    var content = body.get("content");
    var enabled = body.get("enabled");
    var version = body.get("version");
    if ((content == null && enabled == null)
        || (content != null && !content.isString())
        || (enabled != null && !enabled.isBoolean())
        || version == null || !version.isIntegralNumber() || !version.canConvertToLong()
        || version.longValue() < 0) throw new InvalidQuestionRequestException();
    return new UpdateAdminQuestionRequest(content == null ? null : content.stringValue(),
        enabled == null ? null : enabled.booleanValue(), version.longValue());
  }
}
