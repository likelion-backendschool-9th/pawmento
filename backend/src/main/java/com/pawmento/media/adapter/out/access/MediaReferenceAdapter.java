package com.pawmento.media.adapter.out.access;

import com.pawmento.media.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaReferenceAdapter implements InspectMediaReferencesPort {
  private final LoadMediaAssetPort load;
  private final com.pawmento.diary.application.port.in.HasDiaryAttachmentUseCase diary;

  public boolean isReferenced(Long id) {
    return diary.exists(id) || load.findById(id).map(a -> a.getReferenceCount() != 0).orElse(true);
  }
}
