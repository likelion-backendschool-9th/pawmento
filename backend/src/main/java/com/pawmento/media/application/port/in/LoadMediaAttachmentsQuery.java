package com.pawmento.media.application.port.in;

import java.util.List;

public record LoadMediaAttachmentsQuery(Long petId, List<Long> assetIds, String purpose) {
  public LoadMediaAttachmentsQuery(Long petId, List<Long> assetIds) {
    this(petId, assetIds, "DIARY_ATTACHMENT");
  }

  public LoadMediaAttachmentsQuery {
    MediaReferencePurpose.require(purpose);
    assetIds = List.copyOf(assetIds);
  }
}
