package com.pawmento.question.application.port.in;

import java.io.InputStream;
import java.util.function.Supplier;

public record GetQuestionFileResult(
    String filename, String mimeType, long sizeBytes, Supplier<InputStream> content) {}
