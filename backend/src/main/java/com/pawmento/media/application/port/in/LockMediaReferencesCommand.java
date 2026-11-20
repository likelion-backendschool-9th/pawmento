package com.pawmento.media.application.port.in;

import java.util.List;

public record LockMediaReferencesCommand(
    Long memberId, Long petId, List<Long> assetIds, String purpose) {
  public LockMediaReferencesCommand(Long memberId, Long petId, List<Long> assetIds) {
    this(memberId, petId, assetIds, "DIARY_ATTACHMENT");
  }

  public LockMediaReferencesCommand {
    MediaReferencePurpose.require(purpose);
    assetIds = List.copyOf(assetIds);
  }
}
