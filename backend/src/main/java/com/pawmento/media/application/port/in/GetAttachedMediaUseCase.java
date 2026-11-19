package com.pawmento.media.application.port.in;

public interface GetAttachedMediaUseCase {
  GetMediaPreviewResult get(GetAttachedMediaQuery query);
}
