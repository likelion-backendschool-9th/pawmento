package com.pawmento.media.application.port.out;

public interface DeleteMediaFilesPort {
  void deleteAll(String temporaryKey, String storageKey);
}
