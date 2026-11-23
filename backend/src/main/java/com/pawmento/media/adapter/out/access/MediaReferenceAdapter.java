package com.pawmento.media.adapter.out.access;

import com.pawmento.diary.application.port.in.HasDiaryAttachmentUseCase;
import com.pawmento.media.application.port.out.InspectMediaReferencesPort;
import com.pawmento.media.application.port.out.InspectPetProfileReferencePort;
import com.pawmento.media.application.port.out.LoadMediaAssetPort;
import com.pawmento.pet.application.port.in.HasPetProfilePhotoUseCase;
import com.pawmento.question.application.port.in.HasQuestionAttachmentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaReferenceAdapter implements InspectMediaReferencesPort, InspectPetProfileReferencePort {
  private final LoadMediaAssetPort load;
  private final HasDiaryAttachmentUseCase diary;
  private final HasQuestionAttachmentUseCase question;
  private final HasPetProfilePhotoUseCase petPhotos;

  @Override
  public boolean isReferenced(Long id) {
    return diary.exists(id)
        || question.exists(id)
        || petPhotos.exists(id)
        || load.findById(id).map(a -> a.getReferenceCount() != 0).orElse(true);
  }

  @Override
  public boolean isLinked(Long petId, Long assetId) {
    return petPhotos.existsForPet(petId, assetId);
  }
}
