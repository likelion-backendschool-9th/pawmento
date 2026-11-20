package com.pawmento.media.application.port.in;

// Parent domain checks current read access and the actual attachment relationship first.
public record GetAttachedMediaQuery(Long petId, Long assetId, boolean thumbnail, String purpose) {
  public GetAttachedMediaQuery(Long petId, Long assetId, boolean thumbnail) {
    this(petId, assetId, thumbnail, "DIARY_ATTACHMENT");
  }

  public GetAttachedMediaQuery {
    MediaReferencePurpose.require(purpose);
  }
}
