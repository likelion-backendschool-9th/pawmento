package com.pawmento.media.adapter.in.web;

import com.pawmento.media.adapter.in.web.exception.MediaInputUnavailableException;
import java.io.*;
import org.springframework.web.multipart.MultipartFile;

final class MultipartMediaContent extends InputStream {
  private final MultipartFile file;
  private InputStream delegate;

  MultipartMediaContent(MultipartFile file) {
    this.file = file;
  }

  private InputStream input() throws IOException {
    if (delegate == null) delegate = file.getInputStream();
    return delegate;
  }

  @Override
  public int read() throws IOException {
    return input().read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return input().read(b, off, len);
  }

  @Override
  public void close() {
    if (delegate != null)
      try {
        delegate.close();
      } catch (IOException e) {
        throw new MediaInputUnavailableException(e);
      }
  }
}
