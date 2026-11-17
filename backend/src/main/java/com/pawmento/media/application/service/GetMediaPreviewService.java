package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetMediaPreviewService implements GetMediaPreviewUseCase {
  private final CheckMediaPetAccessPort access;
  private final LoadMediaAssetPort load;
  private final ReadMediaFilePort files;
  private final Clock clock;

  @Transactional(readOnly = true)
  public MediaPreviewResult get(GetMediaUploadQuery q, boolean thumbnail) {
    access.requireWriteAccess(q.memberId(), q.petId());
    var a = load.findById(q.assetId()).orElseThrow(() -> new MediaAssetNotFoundException());
    a.requireOwner(q.memberId(), q.petId());
    a.requirePreview(clock.instant());

    var v = a.getVerified();
    if (thumbnail && v.thumbnailSizeBytes() == null) throw new MediaThumbnailNotFoundException();
    String mime = thumbnail ? "image/jpeg" : v.mimeType();
    String extension = mime.equals("audio/mp4") ? "m4a" : mime.equals("image/jpeg") ? "jpg" : "png";
    // Extensions follow the normalized service file, not the untrusted upload name.
    return new MediaPreviewResult(
        "media-" + a.getId() + "." + extension,
        mime,
        thumbnail ? v.thumbnailSizeBytes() : v.sizeBytes(),
        () -> files.open(v.storageKey(), thumbnail ? "thumbnail" : "content"));
  }
}
