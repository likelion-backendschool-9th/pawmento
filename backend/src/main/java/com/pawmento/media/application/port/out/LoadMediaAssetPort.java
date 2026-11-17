package com.pawmento.media.application.port.out;

public interface LoadMediaAssetPort {
  java.util.Optional<com.pawmento.media.domain.MediaAsset> findById(Long assetId);
}
