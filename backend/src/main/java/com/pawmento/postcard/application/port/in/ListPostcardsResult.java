package com.pawmento.postcard.application.port.in;

import java.util.List;

public record ListPostcardsResult(List<GetPostcardResult> items, Long nextCursor) {
  public ListPostcardsResult {
    items = List.copyOf(items);
  }
}
