package com.pawmento.media.application.port.in;

import java.util.List;

public record LockMediaReferencesCommand(Long memberId, Long petId, List<Long> assetIds) {
  public LockMediaReferencesCommand {
    assetIds = List.copyOf(assetIds);
  }
}
