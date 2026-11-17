package com.pawmento.global.security;

// 토큰에 담는 값의 이름. 발급(JwtTokenIssuer)과 검증(SecurityConfig)이 같은 이름을 쓴다
public final class JwtClaims {

	// 회원 역할 (USER, ADMIN). 회원 id는 표준 클레임 sub에 담는다
	public static final String ROLE = "role";

	private JwtClaims() {
	}

}
