package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.DiaryEntry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryResults {
  private final DiaryAttachmentsPort attachments;
  private final DiaryMediaPort media;

  public GetDiaryEntryResult one(DiaryEntry entry) {
    return many(entry.getPetId(), List.of(entry), entry.getCreatedBy()).getFirst();
  }

  public GetDiaryEntryResult one(DiaryEntry entry, Long requester) {
    return many(entry.getPetId(), List.of(entry), requester).getFirst();
  }

  public List<GetDiaryEntryResult> many(Long pet, List<DiaryEntry> entries) {
    return many(pet, entries, null);
  }

  public List<GetDiaryEntryResult> many(Long pet, List<DiaryEntry> entries, Long requester) {
    var links = attachments.findAll(entries.stream().map(DiaryEntry::getId).toList());
    var ids = links.values().stream().flatMap(List::stream).distinct().toList();
    var meta =
        ids.isEmpty()
            ? Map.<Long, DiaryAttachment>of()
            : media.metadata(pet, ids).stream()
                .collect(Collectors.toMap(DiaryAttachment::assetId, Function.identity()));
    return entries.stream()
        .map(
            e ->
                GetDiaryEntryResult.from(
                    e,
                    links.getOrDefault(e.getId(), List.of()).stream().map(meta::get).toList(),
                    requester))
        .toList();
  }
}
