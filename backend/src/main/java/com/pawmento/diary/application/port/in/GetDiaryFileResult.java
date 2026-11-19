package com.pawmento.diary.application.port.in;

import java.io.InputStream;
import java.util.function.Supplier;

public record GetDiaryFileResult(
    String filename, String mimeType, long sizeBytes, Supplier<InputStream> content) {}
