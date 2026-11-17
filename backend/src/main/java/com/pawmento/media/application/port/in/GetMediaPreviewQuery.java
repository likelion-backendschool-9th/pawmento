package com.pawmento.media.application.port.in;

public record GetMediaPreviewQuery(Long memberId, Long petId, Long assetId, boolean thumbnail) {}
