package com.pawmento.member.adapter.out.security;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.pawmento.member.application.port.out.HashPasswordPort;

@Component
class BCryptPasswordHasher implements HashPasswordPort {

	// BCrypt는 72바이트까지만 해시에 쓴다. 그보다 긴 비밀번호는 가입 요청 검증에서 막는다
	static final int MAX_PASSWORD_BYTES = 72;

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	private final String absentMemberPasswordHash = encoder.encode("absent-member-password");

	@Override
	public String hash(String rawPassword) {
		return encoder.encode(rawPassword);
	}

	@Override
	public boolean matches(String rawPassword, String passwordHash) {
		if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			return false;
		}
		return encoder.matches(rawPassword, passwordHash == null ? absentMemberPasswordHash : passwordHash);
	}

}
