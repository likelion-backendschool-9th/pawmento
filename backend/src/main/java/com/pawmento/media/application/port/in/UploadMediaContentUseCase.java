package com.pawmento.media.application.port.in;

public interface UploadMediaContentUseCase {
  GetMediaUploadResult upload(UploadMediaContentCommand input);
}
