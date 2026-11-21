package com.pawmento.postcard.application.port.in;

public record RequestPostcardCommand(
    Long memberId, Long petId, String sourceKind, Long sourceId, String note, boolean consent) {}
