package com.pawmento.question.adapter.in.web;

import com.pawmento.question.adapter.in.web.exception.InvalidQuestionRequestException;
import com.pawmento.question.application.port.in.ListAdminQuestionsQuery;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class QuestionAdminQuery {
  private QuestionAdminQuery() {}

  static ListAdminQuestionsQuery parse(Long member, String raw) {
    var values = new HashMap<String, String>();
    var keys = Set.of("mode", "enabled", "q", "page", "size");
    if (raw != null) for (String pair : raw.split("&", -1)) {
      var parts = pair.split("=", 2);
      String key;
      try { key = decode(parts[0]); }
      catch (IllegalArgumentException e) { continue; }
      if (!keys.contains(key)) continue;
      if (parts.length != 2 || values.containsKey(key)) throw new InvalidQuestionRequestException();
      try { values.put(key, decode(parts[1])); }
      catch (IllegalArgumentException e) { throw new InvalidQuestionRequestException(); }
    }
    String mode = values.get("mode"), enabled = values.get("enabled");
    if (mode != null && !Set.of("ACTIVE", "GARDEN").contains(mode))
      throw new InvalidQuestionRequestException();
    if (enabled != null && !Set.of("true", "false").contains(enabled))
      throw new InvalidQuestionRequestException();
    return ListAdminQuestionsQuery.from(member, mode,
        enabled == null ? null : Boolean.valueOf(enabled), values.get("q"),
        integer(values, "page", 0), integer(values, "size", 20));
  }

  private static String decode(String raw) {
    // URLDecoder 자체는 손상된 UTF-8 바이트를 대체 문자로 바꾸므로 먼저 엄격히 검사한다.
    for (int i = 0; i < raw.length(); i++) {
      if (raw.charAt(i) != '%') continue;
      var bytes = new java.io.ByteArrayOutputStream();
      while (i < raw.length() && raw.charAt(i) == '%') {
        if (i + 2 >= raw.length()) throw new IllegalArgumentException();
        int hi = Character.digit(raw.charAt(i + 1), 16);
        int lo = Character.digit(raw.charAt(i + 2), 16);
        if (hi < 0 || lo < 0) throw new IllegalArgumentException();
        bytes.write((hi << 4) | lo);
        i += 3;
      }
      try {
        StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray()));
      } catch (java.nio.charset.CharacterCodingException failure) {
        throw new IllegalArgumentException(failure);
      }
      i--;
    }
    return URLDecoder.decode(raw, StandardCharsets.UTF_8);
  }

  private static int integer(Map<String, String> values, String key, int fallback) {
    String value = values.get(key);
    if (value == null) return fallback;
    if (!value.matches("[0-9]+")) throw new InvalidQuestionRequestException();
    try { return Integer.parseInt(value); }
    catch (NumberFormatException e) { throw new InvalidQuestionRequestException(); }
  }
}
