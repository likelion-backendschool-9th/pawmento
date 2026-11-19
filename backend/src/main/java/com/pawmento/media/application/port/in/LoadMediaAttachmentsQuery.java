package com.pawmento.media.application.port.in;

import java.util.List;

public record LoadMediaAttachmentsQuery(Long petId, List<Long> assetIds) {
  public LoadMediaAttachmentsQuery {
    assetIds = List.copyOf(assetIds);
  }
}
