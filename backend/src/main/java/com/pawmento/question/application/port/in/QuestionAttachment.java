package com.pawmento.question.application.port.in;

public record QuestionAttachment(
    Long assetId,
    String type,
    String mimeType,
    long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    boolean thumbnail,
    boolean available) {}
