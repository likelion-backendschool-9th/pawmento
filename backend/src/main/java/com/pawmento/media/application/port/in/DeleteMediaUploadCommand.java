package com.pawmento.media.application.port.in;

public record DeleteMediaUploadCommand(Long memberId, Long petId, Long assetId) {}
