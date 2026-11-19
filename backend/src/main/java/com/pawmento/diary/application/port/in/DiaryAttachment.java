package com.pawmento.diary.application.port.in;

public record DiaryAttachment(
    Long assetId,
    String type,
    String mimeType,
    long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    boolean thumbnail,
    boolean available) {}
