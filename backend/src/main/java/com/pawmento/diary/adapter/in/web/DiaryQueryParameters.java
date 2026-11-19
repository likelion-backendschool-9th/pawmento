package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.adapter.in.web.exception.InvalidDiaryRequestException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

// Servlet parameter/DTO 바인딩 전에 원본 쿼리의 중복·빈 값·인코딩을 검사한다.
final class DiaryQueryParameters {
  private final Map<String, String> values;

  private DiaryQueryParameters(Map<String, String> values) {
    this.values = Map.copyOf(values);
  }

  static DiaryQueryParameters parse(String query, String... accepted) {
    var values = new HashMap<String, String>();
    var keys = Set.of(accepted);
    if (query != null && !query.isEmpty()) {
      for (String pair : query.split("&", -1)) {
        String[] parts = pair.split("=", 2);
        String key = decode(parts[0]);
        String value = parts.length == 2 ? decode(parts[1]) : "";
        if (!keys.contains(key)) continue;
        if (value.isEmpty() || values.putIfAbsent(key, value) != null)
          throw new InvalidDiaryRequestException();
      }
    }
    return new DiaryQueryParameters(values);
  }

  String optional(String key) {
    return values.get(key);
  }

  String required(String key) {
    var value = optional(key);
    if (value == null) throw new InvalidDiaryRequestException();
    return value;
  }

  long petId() {
    return number("petId", 1);
  }

  long version() {
    return number("version", 0);
  }

  private long number(String key, long minimum) {
    String value = required(key);
    try {
      if (!value.matches("[0-9]+")) throw new NumberFormatException();
      long result = Long.parseLong(value);
      if (result < minimum) throw new NumberFormatException();
      return result;
    } catch (NumberFormatException e) {
      throw new InvalidDiaryRequestException();
    }
  }

  private static String decode(String value) {
    var bytes = new ByteArrayOutputStream();
    byte[] raw = value.getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < raw.length; i++) {
      if (raw[i] == '%') {
        if (i + 2 >= raw.length) throw new InvalidDiaryRequestException();
        int high = Character.digit((char) raw[++i], 16);
        int low = Character.digit((char) raw[++i], 16);
        if (high < 0 || low < 0) throw new InvalidDiaryRequestException();
        bytes.write(high * 16 + low);
      } else bytes.write(raw[i] == '+' ? ' ' : raw[i]);
    }
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes.toByteArray()))
          .toString();
    } catch (CharacterCodingException e) {
      throw new InvalidDiaryRequestException();
    }
  }
}
