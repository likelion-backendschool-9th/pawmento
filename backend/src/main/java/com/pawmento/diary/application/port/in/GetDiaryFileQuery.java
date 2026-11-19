package com.pawmento.diary.application.port.in;

public record GetDiaryFileQuery(
    Long memberId, Long petId, Long diaryId, Long assetId, boolean thumbnail) {}
