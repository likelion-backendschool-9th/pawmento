package com.pawmento.diary.application.port.in;

import java.util.List;

public record ListDiaryEntriesResult(
    List<GetDiaryEntryResult> items, int page, int size, long totalElements, long totalPages) {
  public ListDiaryEntriesResult {
    items = List.copyOf(items);
  }
}
