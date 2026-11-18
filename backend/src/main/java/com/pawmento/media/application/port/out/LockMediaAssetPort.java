package com.pawmento.media.application.port.out;

public interface LockMediaAssetPort {
  java.util.Optional<com.pawmento.media.domain.MediaAsset> findForUpdate(Long assetId);
}
