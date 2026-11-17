package com.pawmento.media.application.port.in;

public record UploadMediaContentCommand(
    Long memberId, Long petId, Long assetId, java.io.InputStream content) {}
