package com.pawmento.media.application.port.in;

import java.util.List;

public record ChangeMediaReferencesCommand(
    Long memberId, Long petId, List<Long> previous, List<Long> requested, String purpose) {
  public ChangeMediaReferencesCommand(
      Long memberId, Long petId, List<Long> previous, List<Long> requested) {
    this(memberId, petId, previous, requested, "DIARY_ATTACHMENT");
  }

  public ChangeMediaReferencesCommand {
    MediaReferencePurpose.require(purpose);
    previous = List.copyOf(previous);
    requested = List.copyOf(requested);
  }
}
