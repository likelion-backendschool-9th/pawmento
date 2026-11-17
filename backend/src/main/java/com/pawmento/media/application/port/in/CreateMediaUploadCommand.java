package com.pawmento.media.application.port.in;

public record CreateMediaUploadCommand(
    Long memberId,
    Long petId,
    String purpose,
    String filename,
    String mimeType,
    long sizeBytes,
    boolean locationConsent) {}
