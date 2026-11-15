package com.pawmento.member.adapter.in.web.dto;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 검증 규칙과 문구는 프론트엔드의 가입 화면과 같게 둔다
public record SignUpRequest(
		@NotBlank(message = "이메일 주소를 확인해 주세요.")
		@Email(message = "이메일 주소를 확인해 주세요.")
		@Size(max = 254, message = "이메일 주소를 확인해 주세요.")
		String email,

		@NotBlank(message = "비밀번호는 8~72자로 입력해 주세요.")
		@Size(min = 8, max = 72, message = "비밀번호는 8~72자로 입력해 주세요.")
		String password,

		@NotBlank(message = "닉네임은 1~20자로 입력해 주세요.")
		@Size(max = 20, message = "닉네임은 1~20자로 입력해 주세요.")
		String nickname) {

	// BCrypt는 72바이트까지만 해시에 쓴다. 한글은 한 글자가 3바이트라 글자 수 검증만으로는 넘을 수 있다
	@JsonIgnore
	@AssertTrue(message = "비밀번호가 너무 깁니다. 한글은 24자까지 쓸 수 있습니다.")
	public boolean isPasswordWithinHashLimit() {
		return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

	// 로그나 오류 메시지에 비밀번호가 남지 않게 한다
	@Override
	public String toString() {
		return "SignUpRequest[email=" + email + ", password=****, nickname=" + nickname + "]";
	}

}
