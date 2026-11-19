package com.pawmento.diary.application.port.in;

public record DeleteDiaryEntryCommand(Long memberId, Long petId, Long diaryId, Long version) {}
