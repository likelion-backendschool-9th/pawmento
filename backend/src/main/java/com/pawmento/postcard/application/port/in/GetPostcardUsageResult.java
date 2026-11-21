package com.pawmento.postcard.application.port.in;

public record GetPostcardUsageResult(
    boolean beta, int dailyLimit, long usedToday, long remainingToday) {}
