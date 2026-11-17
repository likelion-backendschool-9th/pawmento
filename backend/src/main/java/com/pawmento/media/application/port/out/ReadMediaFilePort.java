package com.pawmento.media.application.port.out;

public interface ReadMediaFilePort {
  java.io.InputStream open(String key, String variant);
}
