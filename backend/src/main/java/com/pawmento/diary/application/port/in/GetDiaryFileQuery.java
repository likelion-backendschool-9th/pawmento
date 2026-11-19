package com.pawmento.diary.application.port.in;

public record GetDiaryFileQuery(
    Long memberId, Long petId, Long recordId, Long assetId, boolean thumbnail) {}
