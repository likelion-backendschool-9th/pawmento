package com.pawmento.media.application.port.in;

public interface CreateMediaUploadUseCase {
  MediaUploadResult create(CreateMediaUploadCommand input);
}
