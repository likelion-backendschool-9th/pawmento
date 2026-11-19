package com.pawmento.media.application.port.out;

public interface LoadMediaAssetPort {
  java.util.List<com.pawmento.media.domain.MediaAsset> findAll(java.util.List<Long> assetIds);

  java.util.Optional<com.pawmento.media.domain.MediaAsset> findById(Long assetId);
}
