package com.pawmento.media.application.port.out;

public interface WriteMediaFilePort {
  void write(String key, String variant, java.io.InputStream content, long maxBytes);
}
