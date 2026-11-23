package com.pawmento.postcard.adapter.in.web;

import com.pawmento.postcard.adapter.in.web.exception.InvalidPostcardRequestException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

// 같은 키가 둘이거나 헤더로 채워진 값을 받지 않도록 쿼리 원본을 직접 읽는다(다이어리·질문과 같은 방식).
final class PostcardQueryParameters {
  private final Map<String, String> values;

  private PostcardQueryParameters(Map<String, String> values) {
    this.values = values;
  }

  static PostcardQueryParameters parse(String raw, String... accepted) {
    var keys = Set.of(accepted);
    var values = new HashMap<String, String>();
    if (raw != null && !raw.isEmpty())
      for (String pair : raw.split("&", -1)) {
        var parts = pair.split("=", 2);
        String key;
        try {
          key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
          continue;
        }
        if (!keys.contains(key)) continue;
        if (values.containsKey(key) || parts.length != 2)
          throw new InvalidPostcardRequestException("요청 값이 올바르지 않아요.");
        try {
          values.put(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
          throw new InvalidPostcardRequestException("요청 값이 올바르지 않아요.");
        }
      }
    return new PostcardQueryParameters(values);
  }

  Long petId() {
    Long id = id("petId");
    if (id == null) throw new InvalidPostcardRequestException("반려동물을 골라 주세요.");
    return id;
  }

  Long id(String key) {
    String value = values.get(key);
    if (value == null) return null;
    return positive(value);
  }

  int size(int fallback, int max) {
    String value = values.get("size");
    if (value == null) return fallback;
    long n = positive(value);
    if (n > max) throw new InvalidPostcardRequestException("size는 1부터 " + max + "까지예요.");
    return (int) n;
  }

  static long positive(String value) {
    if (!value.matches("[0-9]{1,18}"))
      throw new InvalidPostcardRequestException("요청 값이 올바르지 않아요.");
    long n = Long.parseLong(value);
    if (n <= 0) throw new InvalidPostcardRequestException("요청 값이 올바르지 않아요.");
    return n;
  }
}
