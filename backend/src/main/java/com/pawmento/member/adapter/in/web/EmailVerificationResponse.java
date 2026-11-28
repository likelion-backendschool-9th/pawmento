package com.pawmento.member.adapter.in.web;

import java.time.Instant;

import com.pawmento.member.application.port.in.SendEmailVerificationResult;

// 인증번호 원문은 메일로만 보내고 응답에는 싣지 않는다
public record EmailVerificationResponse(String email, Instant expiresAt, Instant retryAfter, long expiresInSeconds,
		long retryAfterSeconds) {

	static EmailVerificationResponse from(SendEmailVerificationResult result) {
		return new EmailVerificationResponse(result.email(), result.expiresAt(), result.retryAfter(),
				result.expiresInSeconds(), result.retryAfterSeconds());
	}

}