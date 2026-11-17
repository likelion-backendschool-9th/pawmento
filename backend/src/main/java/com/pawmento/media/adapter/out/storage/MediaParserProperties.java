package com.pawmento.media.adapter.out.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("pawmento.media.parser")
public record MediaParserProperties(
    @DefaultValue("ffmpeg") String ffmpeg,
    @DefaultValue("ffprobe") String ffprobe,
    @DefaultValue("30") int timeoutSeconds) {
  public MediaParserProperties {
    if (timeoutSeconds < 1 || timeoutSeconds > 60)
      throw new IllegalArgumentException("Parser timeout must be 1..60 seconds");
  }
}
