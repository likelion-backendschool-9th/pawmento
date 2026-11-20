package com.pawmento.question.adapter.in.web;

import com.pawmento.question.application.port.in.GetQuestionFileResult;
import java.io.InputStream;
import org.springframework.core.io.AbstractResource;

final class QuestionFileResource extends AbstractResource {
  private final GetQuestionFileResult file;

  QuestionFileResource(GetQuestionFileResult file) {
    this.file = file;
  }

  public String getDescription() {
    return "Authenticated question attachment";
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
