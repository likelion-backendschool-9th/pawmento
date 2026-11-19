package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.GetDiaryFileResult;
import java.io.InputStream;
import org.springframework.core.io.AbstractResource;

final class DiaryFileResource extends AbstractResource {
  private final GetDiaryFileResult file;

  DiaryFileResource(GetDiaryFileResult file) {
    this.file = file;
  }

  public String getDescription() {
    return "Authenticated diary attachment";
  }

  public String getFilename() {
    return file.filename();
  }

  public long contentLength() {
    return file.sizeBytes();
  }

  public InputStream getInputStream() {
    return file.content().get();
  }
}
