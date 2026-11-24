package com.pawmento.diary.adapter.in.web;

import tools.jackson.databind.JsonNode;

public record EditDiaryEntryRequest(
    JsonNode content,
    JsonNode recordedDate,
    JsonNode version,
    JsonNode attachmentIds,
    JsonNode location,
    JsonNode author,
    JsonNode authorNameSnapshot,
    JsonNode displayName) {}
