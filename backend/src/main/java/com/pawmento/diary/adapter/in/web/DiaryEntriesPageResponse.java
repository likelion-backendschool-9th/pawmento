package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.ListDiaryEntriesResult;
import java.util.List;

public record DiaryEntriesPageResponse(
    List<DiaryEntryResponse> items, int page, int size, long totalElements, long totalPages) {
  static DiaryEntriesPageResponse from(ListDiaryEntriesResult result) {
    return new DiaryEntriesPageResponse(
        result.items().stream().map(DiaryEntryResponse::from).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }
}
