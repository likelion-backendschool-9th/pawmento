package com.pawmento.member.application.port.in;

// 용도는 웹이 도메인 enum을 모르도록 문자열로 받는다
public record SendEmailVerificationCommand(String email, String purpose) {
}