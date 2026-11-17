package com.pawmento.media.adapter.in.web;

import com.pawmento.media.application.port.in.MediaPreviewResult;
import java.io.InputStream;
import org.springframework.core.io.AbstractResource;

final class MediaPreviewResource extends AbstractResource {
  private final MediaPreviewResult media;

  MediaPreviewResource(MediaPreviewResult media) {
    this.media = media;
  }

  public String getDescription() {
    return "Authenticated media";
  }

  public String getFilename() {
    return media.filename();
  }

  public long contentLength() {
    return media.sizeBytes();
  }

  public InputStream getInputStream() {
    return media.content().get();
  }
}
