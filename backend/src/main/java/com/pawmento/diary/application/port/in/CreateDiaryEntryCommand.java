package com.pawmento.diary.application.port.in;

public record CreateDiaryEntryCommand(
    Long memberId, Long petId, String content, java.time.LocalDate recordedDate) {}
