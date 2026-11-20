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
    String mode = value.get("mode").asText();
    if (!Set.of("NONE", "MANUAL", "AUTO").contains(mode)) throw new InvalidDiaryRequestException();
    Set<String> allowed =
        mode.equals("MANUAL")
            ? Set.of("mode", "latitude", "longitude", "placeName")
            : Set.of("mode");
    long known = allowed.stream().filter(value::has).count();
    if (known != value.size()) throw new InvalidDiaryRequestException();
    return switch (mode) {
      case "NONE" -> DiaryLocationChange.none();
      case "AUTO" -> DiaryLocationChange.auto();
      default ->
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
