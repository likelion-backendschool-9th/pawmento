package com.pawmento.media.adapter.out.access;

import com.pawmento.media.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaReferenceAdapter implements InspectMediaReferencesPort {
  private final LoadMediaAssetPort load;
  private final com.pawmento.diary.application.port.in.HasDiaryAttachmentUseCase diary;

  private final com.pawmento.question.application.port.in.HasQuestionAttachmentUseCase question;

  public boolean isReferenced(Long id) {
    return diary.exists(id)
        || question.exists(id)
        || load.findById(id).map(a -> a.getReferenceCount() != 0).orElse(true);
  }
}
