package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAttachedMediaService implements GetAttachedMediaUseCase {
  private final LoadMediaAssetPort load;
  private final ReadMediaFilePort files;

  @Transactional(readOnly = true)
  public GetMediaPreviewResult get(GetAttachedMediaQuery q) {
    var a = load.findById(q.assetId()).orElseThrow(MediaAssetNotFoundException::new);
    a.requireAttached(q.petId());
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
