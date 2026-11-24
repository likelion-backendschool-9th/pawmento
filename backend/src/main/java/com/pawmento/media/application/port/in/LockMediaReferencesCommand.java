package com.pawmento.media.application.port.in;

import java.util.List;

/** previous는 호출자가 권한을 확인한 뒤 DB에서 읽은 실제 연결이다. */
public record LockMediaReferencesCommand(
    Long memberId, Long petId, List<Long> previous, List<Long> requested, String purpose) {
  public LockMediaReferencesCommand(Long memberId, Long petId, List<Long> assetIds) {
    this(memberId, petId, assetIds, "DIARY_ATTACHMENT");
  }

  public LockMediaReferencesCommand(Long memberId, Long petId, List<Long> assetIds, String purpose) {
    this(memberId, petId, List.of(), assetIds, purpose);
  }

  public LockMediaReferencesCommand {
    MediaReferencePurpose.require(purpose);
    previous = List.copyOf(previous);
    requested = List.copyOf(requested);
  }
}
