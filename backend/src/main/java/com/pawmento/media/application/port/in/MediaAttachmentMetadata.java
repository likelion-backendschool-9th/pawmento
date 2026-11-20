package com.pawmento.media.application.port.in;

public record MediaAttachmentMetadata(
    Long assetId,
    String type,
    String mimeType,
    long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    boolean thumbnail,
    boolean available,
    String filename) {
  public MediaAttachmentMetadata(
      Long assetId,
      String type,
      String mimeType,
      long sizeBytes,
      Long durationMs,
      Integer width,
      Integer height,
      boolean thumbnail,
      boolean available) {
    this(assetId, type, mimeType, sizeBytes, durationMs, width, height, thumbnail, available, null);
  }
}
