package com.pawmento.diary.adapter.in.web;

import tools.jackson.databind.JsonNode;

public record CreateDiaryEntryRequest(
    JsonNode content, JsonNode recordedDate, JsonNode attachmentIds, JsonNode location) {}
