package com.pawmento.question.adapter.in.web;

import com.pawmento.global.exception.RuleViolationException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class QuestionPetQuery {
  private QuestionPetQuery() {}

  static Long petId(String raw) {
    String value = null;
    if (raw != null)
      for (String pair : raw.split("&", -1)) {
        var parts = pair.split("=", 2);
        String key;
        try {
          key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
          continue;
        }
        if (!"petId".equals(key)) continue;
        if (value != null || parts.length != 2) throw new InvalidQuestionRequestException();
        try {
          value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
          throw new InvalidQuestionRequestException();
        }
      }
    if (value == null || !value.matches("[0-9]+")) throw new InvalidQuestionRequestException();
    try {
      long id = Long.parseLong(value);
      if (id <= 0) throw new InvalidQuestionRequestException();
      return id;
    } catch (NumberFormatException e) {
      throw new InvalidQuestionRequestException();
    }
  }

  static final class InvalidQuestionRequestException extends RuleViolationException {
    InvalidQuestionRequestException() {
      super("petId는 필수 양의 정수 쿼리입니다.");
    }
  }
}
