package com.pawmento.media.application.port.in;

public interface GetMediaUploadUseCase {
  MediaUploadResult get(GetMediaUploadQuery input);
}
