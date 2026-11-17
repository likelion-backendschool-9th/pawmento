package com.pawmento.member.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

// 가입 규칙(길이 등)은 검사하지 않는다. 규칙이 바뀌어도 예전에 가입한 회원이 로그인할 수 있어야 한다
public record LoginRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		String email,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		String password) {

	// 로그나 오류 메시지에 비밀번호가 남지 않게 한다
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=****]";
	}

}
