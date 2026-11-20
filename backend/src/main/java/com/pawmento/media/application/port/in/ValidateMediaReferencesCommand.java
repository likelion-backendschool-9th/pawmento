package com.pawmento.media.application.port.in;

import java.util.List;

public record ValidateMediaReferencesCommand(
    Long memberId, Long petId, List<Long> previous, List<Long> requested, String purpose) {
  public ValidateMediaReferencesCommand(
      Long memberId, Long petId, List<Long> previous, List<Long> requested) {
    this(memberId, petId, previous, requested, "DIARY_ATTACHMENT");
  }

  public ValidateMediaReferencesCommand {
    MediaReferencePurpose.require(purpose);
    previous = List.copyOf(previous);
    requested = List.copyOf(requested);
  }
}
