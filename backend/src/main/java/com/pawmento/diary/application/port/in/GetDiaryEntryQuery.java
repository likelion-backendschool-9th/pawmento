package com.pawmento.diary.application.port.in;

public record GetDiaryEntryQuery(Long memberId, Long petId, Long diaryId) {}
