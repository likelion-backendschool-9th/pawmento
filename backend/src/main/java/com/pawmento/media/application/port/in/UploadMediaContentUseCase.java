package com.pawmento.media.application.port.in;

public interface UploadMediaContentUseCase {
  MediaUploadResult upload(UploadMediaContentCommand input);
}
