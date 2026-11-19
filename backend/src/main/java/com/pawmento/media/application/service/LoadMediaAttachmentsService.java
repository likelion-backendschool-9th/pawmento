package com.pawmento.media.application.service;

import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.LoadMediaAssetPort;
import com.pawmento.media.domain.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadMediaAttachmentsService implements LoadMediaAttachmentsUseCase {
  private final LoadMediaAssetPort load;

  @Transactional(readOnly = true)
  public List<MediaAttachmentMetadata> load(LoadMediaAttachmentsQuery q) {
    var assets =
        load.findAll(q.assetIds()).stream()
            .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
    return q.assetIds().stream()
        .map(
            id -> {
              var a = assets.get(id);
              if (a == null
                  || !a.getPetId().equals(q.petId())
                  || a.getPurpose() != MediaPurpose.DIARY_ATTACHMENT)
                return new MediaAttachmentMetadata(
                    id, null, null, 0, null, null, null, false, false);
              var v = a.getVerified();
              boolean available =
                  v != null
                      && a.getStatus() == MediaUploadStatus.READY
                      && a.getReferenceCount() > 0;
              return new MediaAttachmentMetadata(
                  id,
                  a.getMediaType().name(),
                  v == null ? null : v.mimeType(),
                  v == null ? 0 : v.sizeBytes(),
                  v == null ? null : v.durationMs(),
                  v == null ? null : v.width(),
                  v == null ? null : v.height(),
                  v != null && v.thumbnailSizeBytes() != null,
                  available);
            })
        .toList();
  }
}
