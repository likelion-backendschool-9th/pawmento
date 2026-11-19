package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.LoadMediaAssetPort;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadMediaAttachmentsService implements LoadMediaAttachmentsUseCase {
  private final LoadMediaAssetPort load;

  @Transactional(readOnly = true)
  public List<MediaAttachmentMetadata> load(LoadMediaAttachmentsQuery q) {
    var assets = load.findAll(q.assetIds());
    if (assets.size() != new HashSet<>(q.assetIds()).size())
      throw new MediaAssetNotFoundException();
    return assets.stream()
        .map(
            a -> {
              a.requireAttached(q.petId());
              var v = a.getVerified();
              return new MediaAttachmentMetadata(
                  a.getId(),
                  a.getMediaType().name(),
                  v.mimeType(),
                  v.sizeBytes(),
                  v.durationMs(),
                  v.width(),
                  v.height(),
                  v.thumbnailSizeBytes() != null);
            })
        .toList();
  }
}
