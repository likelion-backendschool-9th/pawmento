package com.pawmento.media.application.port.out;

public interface StoreMediaUploadPort {
  com.pawmento.media.domain.StoredUpload store(
      String key, java.io.InputStream input, long maxBytes);
}
