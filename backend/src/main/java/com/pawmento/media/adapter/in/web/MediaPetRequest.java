package com.pawmento.media.adapter.in.web;

import com.pawmento.media.adapter.in.web.exception.InvalidMediaRequestException;

// HTTP headers and multipart fields must never supply the required query value.
public record MediaPetRequest(Long petId) {
  // Never call getParameter*: containers may parse multipart when those methods are used.
  static MediaPetRequest fromQuery(String query) {
    String value = null;
    try {
      if (query != null)
        for (String item : query.split("&", -1)) {
          String[] pair = item.split("=", 2);
          String key = java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8);
          if (!key.equals("petId")) continue;
          if (value != null || pair.length != 2) throw new IllegalArgumentException();
          value = java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8);
        }
      if (value == null) throw new IllegalArgumentException();
      long id = Long.parseLong(value);
      if (id <= 0) throw new IllegalArgumentException();
      return new MediaPetRequest(id);
    } catch (IllegalArgumentException e) {
      throw new InvalidMediaRequestException(InvalidMediaRequestException.Reason.INVALID_PET_QUERY);
    }
  }
}
