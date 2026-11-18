package com.pawmento.media.application.port.in;

public interface CreateMediaUploadUseCase {
  GetMediaUploadResult create(CreateMediaUploadCommand input);
}
