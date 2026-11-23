package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.MediaPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAttachedMediaService implements GetAttachedMediaUseCase {
  private final LoadMediaAssetPort load;
  private final ReadMediaFilePort files;
  private final InspectPetProfileReferencePort petProfiles;

  @Transactional(readOnly = true)
  public GetMediaPreviewResult get(GetAttachedMediaQuery q) {
    var a = load.findById(q.assetId()).orElseThrow(MediaAssetNotFoundException::new);
    var purpose = MediaPurpose.valueOf(q.purpose());
    if (purpose == MediaPurpose.PET_PROFILE) {
      if (!petProfiles.isLinked(q.petId(), q.assetId())) throw new MediaAssetNotFoundException();
      a.requireLinkedPetProfile(q.petId());
    } else {
      a.requireAttached(q.petId(), purpose);
    }
    var v = a.getVerified();
    if (q.thumbnail() && v.thumbnailSizeBytes() == null)
      throw new MediaThumbnailNotFoundException();
    String mime = q.thumbnail() ? "image/jpeg" : v.mimeType();
    String ext = mime.equals("audio/mp4") ? "m4a" : mime.equals("image/jpeg") ? "jpg" : "png";
    return new GetMediaPreviewResult(
        "media-" + a.getId() + "." + ext,
        mime,
        q.thumbnail() ? v.thumbnailSizeBytes() : v.sizeBytes(),
        () -> files.open(v.storageKey(), q.thumbnail() ? "thumbnail" : "content"));
  }
}
