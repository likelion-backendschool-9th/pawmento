package com.pawmento.diary.application.port.out;

public record DiaryMapSnapshot(
    Long rowId,
    Long diaryId,
    Long petId,
    Long createdBy,
    double latitude,
    double longitude,
    String placeName,
    String source,
    Long sourceAssetId,
    long revision) {}
