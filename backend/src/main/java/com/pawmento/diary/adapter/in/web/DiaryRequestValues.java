package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.adapter.in.web.exception.InvalidDiaryRequestException;
import com.pawmento.diary.application.port.in.DiaryWriteFeatures;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import tools.jackson.databind.JsonNode;

final class DiaryRequestValues {
  private DiaryRequestValues() {}

  static String text(JsonNode value, boolean required) {
    if (value == null && !required) return null;
    if (value == null || !value.isString()) throw new InvalidDiaryRequestException();
    return value.asText();
  }

  static LocalDate date(JsonNode value, boolean required) {
    String text = text(value, required);
    if (text == null) return null;
    if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) throw new InvalidDiaryRequestException();
    try {
      return LocalDate.parse(text);
    } catch (DateTimeException e) {
      throw new InvalidDiaryRequestException();
    }
  }

  static Long version(JsonNode value) {
    if (value == null
        || !value.isIntegralNumber()
        || !value.canConvertToLong()
        || value.asLong() < 0) throw new InvalidDiaryRequestException();
    return value.asLong();
  }

  static DiaryWriteFeatures features(JsonNode attachments, JsonNode location) {
    if (attachments != null && !attachments.isArray()) throw new InvalidDiaryRequestException();
    boolean locationRequested = false;
    if (location != null) {
      if (!location.isObject()
          || !location.has("mode")
          || !location.get("mode").isString()
          || !java.util.Set.of("AUTO", "MANUAL", "NONE").contains(location.get("mode").asText()))
        throw new InvalidDiaryRequestException();
      locationRequested = !"NONE".equals(location.get("mode").asText());
      if (!locationRequested && location.size() != 1) throw new InvalidDiaryRequestException();
    }
    return new DiaryWriteFeatures(attachments != null && !attachments.isEmpty(), locationRequested);
  }

  // Servlet parameter binding would also accept form fields. Read the query only, once decoded.
  static Long deleteVersion(String query) {
    String value = null;
    try {
      if (query != null)
        for (String item : query.split("&", -1)) {
          String[] pair = item.split("=", 2);
          if (!"version".equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) continue;
          if (value != null || pair.length != 2) throw new IllegalArgumentException();
          value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
      if (value == null) throw new IllegalArgumentException();
      long version = Long.parseLong(value);
      if (version < 0) throw new IllegalArgumentException();
      return version;
    } catch (IllegalArgumentException e) {
      throw new InvalidDiaryRequestException();
    }
  }

  static void id(Long id) {
    if (id == null || id <= 0) throw new InvalidDiaryRequestException();
  }
}
