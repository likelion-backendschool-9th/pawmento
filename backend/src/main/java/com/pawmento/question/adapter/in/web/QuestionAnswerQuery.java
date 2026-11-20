package com.pawmento.question.adapter.in.web;

import com.pawmento.question.adapter.in.web.exception.InvalidQuestionRequestException;
import com.pawmento.question.application.port.in.ListQuestionAnswersQuery;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

final class QuestionAnswerQuery {
  private final Map<String, String> values = new HashMap<>();

  QuestionAnswerQuery(String raw) {
    if (raw == null) return;
    for (String pair : raw.split("&", -1)) {
      var parts = pair.split("=", 2);
      String key;
      try {
        key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        continue;
      }
      if (!Set.of(
              "petId",
              "mode",
              "promptId",
              "authorMemberId",
              "from",
              "to",
              "page",
              "size",
              "version")
          .contains(key)) continue;
      if (parts.length != 2 || values.containsKey(key)) throw new InvalidQuestionRequestException();
      try {
        values.put(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
      } catch (IllegalArgumentException e) {
        throw new InvalidQuestionRequestException();
      }
    }
  }

  Long petId() {
    return number("petId", true, 1);
  }

  long version() {
    return number("version", true, 0);
  }

  ListQuestionAnswersQuery list(Long member) {
    String mode = values.get("mode");
    if (mode != null && !Set.of("ACTIVE", "GARDEN").contains(mode))
      throw new InvalidQuestionRequestException();
    return ListQuestionAnswersQuery.from(
        member,
        petId(),
        mode,
        number("promptId", false, 1),
        number("authorMemberId", false, 1),
        date("from"),
        date("to"),
        integer("page", 0, 0, Integer.MAX_VALUE),
        integer("size", 20, 1, 100));
  }

  private int integer(String key, int defaultValue, int min, int max) {
    Long n = number(key, false, min);
    if (n == null) return defaultValue;
    if (n > max) throw new InvalidQuestionRequestException();
    return n.intValue();
  }

  private Long number(String key, boolean required, long min) {
    String value = values.get(key);
    if (value == null && !required) return null;
    if (value == null || !value.matches("[0-9]+")) throw new InvalidQuestionRequestException();
    try {
      long n = Long.parseLong(value);
      if (n < min) throw new InvalidQuestionRequestException();
      return n;
    } catch (NumberFormatException e) {
      throw new InvalidQuestionRequestException();
    }
  }

  private LocalDate date(String key) {
    String value = values.get(key);
    if (value == null) return null;
    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new InvalidQuestionRequestException();
    try {
      return LocalDate.parse(value);
    } catch (java.time.DateTimeException e) {
      throw new InvalidQuestionRequestException();
    }
  }
}
