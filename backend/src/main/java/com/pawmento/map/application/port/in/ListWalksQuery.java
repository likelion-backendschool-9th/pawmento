package com.pawmento.map.application.port.in;

import java.time.LocalDate;

public record ListWalksQuery(Long memberId, Long petId, LocalDate date) {
}
