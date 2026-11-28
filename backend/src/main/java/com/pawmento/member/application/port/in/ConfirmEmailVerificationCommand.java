package com.pawmento.member.application.port.in;

public record ConfirmEmailVerificationCommand(String email, String purpose, String code) {
}
