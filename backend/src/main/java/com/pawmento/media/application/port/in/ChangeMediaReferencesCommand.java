package com.pawmento.media.application.port.in;

import java.util.List;

public record ChangeMediaReferencesCommand(
    Long memberId, Long petId, List<Long> previous, List<Long> requested) {
  public ChangeMediaReferencesCommand {
    previous = List.copyOf(previous);
    requested = List.copyOf(requested);
  }
}
