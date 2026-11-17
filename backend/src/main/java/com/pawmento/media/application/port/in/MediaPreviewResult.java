package com.pawmento.media.application.port.in;

import java.io.InputStream;
import java.util.function.Supplier;

public record MediaPreviewResult(
    String filename, String mimeType, long sizeBytes, Supplier<InputStream> content) {}
