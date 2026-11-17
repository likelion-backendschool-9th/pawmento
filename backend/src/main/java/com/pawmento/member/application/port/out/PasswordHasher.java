package com.pawmento.member.application.port.out;

// 비밀번호 해시. 애플리케이션 계층이 해시 알고리즘(BCrypt)과 Spring Security를 모르게 한다
public interface PasswordHasher {

	String hash(String rawPassword);

	// 저장된 해시가 없으면(null) 회원 부재를 숨기기 위한 비교만 수행한다.
	boolean matches(String rawPassword, String passwordHash);

}
