package com.pawmento.media.application.port.out;

import com.pawmento.media.domain.MediaPurpose;

public record MediaOwnership(
    Long assetId, Long uploaderMemberId, Long petId, MediaPurpose purpose) {}
