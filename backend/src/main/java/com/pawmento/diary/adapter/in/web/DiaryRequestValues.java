package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.adapter.in.web.exception.InvalidDiaryRequestException;
import com.pawmento.diary.application.port.in.DiaryWriteFeatures;
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
    var locationChange = DiaryLocationRequestValues.parse(location);
    java.util.List<Long> ids = null;
    if (attachments != null) {
      ids = new java.util.ArrayList<>();
      for (JsonNode id : attachments) {
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0)
          throw new InvalidDiaryRequestException();
        ids.add(id.asLong());
      }
      if (new java.util.HashSet<>(ids).size() != ids.size())
        throw new InvalidDiaryRequestException();
    }
    return new DiaryWriteFeatures(ids, locationChange);
  }

  static void rejectAuthor(JsonNode... fields) {
    for (var field : fields) if (field != null) throw new InvalidDiaryRequestException();
  }

  static void id(Long id) {
    if (id == null || id <= 0) throw new InvalidDiaryRequestException();
  }
}
