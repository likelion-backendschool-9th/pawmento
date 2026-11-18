package com.pawmento.media.application.port.out;

public interface SaveMediaAssetPort {
  com.pawmento.media.domain.MediaAsset save(com.pawmento.media.domain.MediaAsset asset);
}
