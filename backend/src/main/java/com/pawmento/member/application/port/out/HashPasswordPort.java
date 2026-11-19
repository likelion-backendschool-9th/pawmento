package com.pawmento.member.application.port.out;

// 비밀번호 해시. 애플리케이션 계층이 해시 알고리즘(BCrypt)과 Spring Security를 모르게 한다
public interface HashPasswordPort {

	String hash(String rawPassword);

	// 저장된 해시가 없으면(null) 시간만 맞추는 비교를 하고 항상 false를 돌려준다.
	boolean matches(String rawPassword, String passwordHash);

}
