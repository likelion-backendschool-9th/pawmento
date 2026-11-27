package com.pawmento.member.application.port.out;

import java.time.Instant;

import com.pawmento.member.domain.VerificationPurpose;

public interface SendVerificationMailPort {
	// 보내지 못하면 VerificationMailUnavailableException
	void send(String email, VerificationPurpose purpose, String code, Instant expiresAt);
}