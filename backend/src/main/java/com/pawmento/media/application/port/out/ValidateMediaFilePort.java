package com.pawmento.media.application.port.out;

public interface ValidateMediaFilePort {
  com.pawmento.media.domain.VerifiedMedia validate(
      com.pawmento.media.domain.MediaAsset asset, String outputKey);
}
