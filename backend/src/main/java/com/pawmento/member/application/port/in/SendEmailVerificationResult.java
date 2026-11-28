package com.pawmento.member.application.port.in;

import java.time.Instant;

// 남은 초는 화면이 기기 시계와 상관없이 카운트다운을 하도록 함께 준다 (#279-S12)
public record SendEmailVerificationResult(String email, Instant expiresAt, Instant retryAfter, long expiresInSeconds,
		long retryAfterSeconds) {
}