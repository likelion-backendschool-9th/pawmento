package com.pawmento.media.adapter.out.access;

import com.pawmento.media.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnattachedMediaReferenceAdapter implements InspectMediaReferencesPort {
  private final LoadMediaAssetPort load;

  public boolean isReferenced(Long id) {
    return load.findById(id).map(a -> a.getReferenceCount() != 0).orElse(true);
  }
}
