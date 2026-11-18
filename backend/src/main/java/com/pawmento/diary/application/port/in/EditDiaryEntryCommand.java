package com.pawmento.diary.application.port.in;

public record EditDiaryEntryCommand(
    Long memberId,
    Long petId,
    Long recordId,
    String content,
    java.time.LocalDate recordedDate,
    Long version) {}
