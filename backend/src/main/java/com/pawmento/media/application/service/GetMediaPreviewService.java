package com.pawmento.media.application.service;

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
    var a =
        load.findById(q.assetId())
            .orElseThrow(
                () ->
                    new MediaUploadException(MediaUploadException.Kind.NOT_FOUND, "파일을 찾을 수 없어요."));
    try {
      a.requireOwner(q.memberId(), q.petId());
      a.requirePreview(clock.instant());
    } catch (MediaViolation e) {
      throw MediaUploadException.from(e);
    }
    var v = a.getVerified();
    if (thumbnail && v.thumbnailSizeBytes() == null)
      throw new MediaUploadException(MediaUploadException.Kind.NOT_FOUND, "썸네일이 없어요.");
    String mime = thumbnail ? "image/jpeg" : v.mimeType();
    String extension = mime.equals("audio/mp4") ? "m4a" : mime.equals("image/jpeg") ? "jpg" : "png";
    // Extensions follow the normalized service file, not the untrusted upload name.
    return new MediaPreviewResult(
        "media-" + a.getId() + "." + extension,
        mime,
        thumbnail ? v.thumbnailSizeBytes() : v.sizeBytes(),
        () -> {
          try {
            return files.open(v.storageKey(), thumbnail ? "thumbnail" : "content");
          } catch (MediaStorageException e) {
            throw new MediaUploadException(
                MediaUploadException.Kind.STORAGE, "파일을 읽지 못했어요. 잠시 후 다시 시도해 주세요.");
          }
        });
  }
}
