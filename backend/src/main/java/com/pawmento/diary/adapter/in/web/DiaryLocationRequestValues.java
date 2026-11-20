package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.adapter.in.web.exception.InvalidDiaryRequestException;
import com.pawmento.diary.application.port.in.DiaryLocationChange;
import java.util.Set;
import tools.jackson.databind.JsonNode;

final class DiaryLocationRequestValues {
  private DiaryLocationRequestValues() {}

  static DiaryLocationChange parse(JsonNode value) {
    if (value == null) return DiaryLocationChange.keep();
    if (!value.isObject() || !value.has("mode") || !value.get("mode").isString())
      throw new InvalidDiaryRequestException();
    DiaryLocationChange.Kind mode;
    try {
      mode = DiaryLocationChange.Kind.valueOf(value.get("mode").asText());
    } catch (IllegalArgumentException e) {
      throw new InvalidDiaryRequestException();
    }
    if (mode == DiaryLocationChange.Kind.KEEP) throw new InvalidDiaryRequestException();
    Set<String> allowed =
        mode == DiaryLocationChange.Kind.MANUAL
            ? Set.of("mode", "latitude", "longitude", "placeName")
            : Set.of("mode");
    long known = allowed.stream().filter(value::has).count();
    if (known != value.size()) throw new InvalidDiaryRequestException();
    return switch (mode) {
      case NONE -> DiaryLocationChange.none();
      case AUTO -> DiaryLocationChange.auto();
      case KEEP -> throw new InvalidDiaryRequestException();
      case MANUAL ->
          DiaryLocationChange.manual(
              coordinate(value.get("latitude")),
              coordinate(value.get("longitude")),
              name(value.get("placeName")));
    };
  }

  private static Double coordinate(JsonNode value) {
    if (value == null || !value.isNumber()) throw new InvalidDiaryRequestException();
    return value.asDouble();
  }

  private static String name(JsonNode value) {
    if (value == null || value.isNull()) return null;
    if (!value.isString()) throw new InvalidDiaryRequestException();
    return value.asText();
  }
}
