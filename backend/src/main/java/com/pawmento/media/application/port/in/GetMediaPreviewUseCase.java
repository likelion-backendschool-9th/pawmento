package com.pawmento.media.application.port.in;

public interface GetMediaPreviewUseCase {
  MediaPreviewResult get(GetMediaUploadQuery query, boolean thumbnail);
}
