package com.pawmento.member.application.port.in;

public interface SendEmailVerificationUseCase {
	SendEmailVerificationResult send(SendEmailVerificationCommand command);
}