package com.pawmento.media.application.port.in;

import java.util.List;

public record ValidateMediaReferencesCommand(
    Long memberId, Long petId, List<Long> previous, List<Long> requested) {
  public ValidateMediaReferencesCommand {
    previous = List.copyOf(previous);
    requested = List.copyOf(requested);
  }
}
