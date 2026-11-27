package com.pawmento.member.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 검증 규칙과 문구는 프론트엔드의 가입 화면과 같게 둔다
public record SignUpMemberRequest(
		@NotBlank(message = "이메일 주소를 확인해 주세요.")
		@Email(message = "이메일 주소를 확인해 주세요.")
		@Size(max = 254, message = "이메일 주소를 확인해 주세요.")
		String email,

		// BCrypt는 72바이트까지만 해시에 쓴다. 한글은 한 글자가 3바이트라 글자 수 검증만으로는 넘을 수 있다
		@NotBlank(message = "비밀번호는 8~72자로 입력해 주세요.")
		@Size(min = 8, max = 72, message = "비밀번호는 8~72자로 입력해 주세요.")
		@MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다. 한글은 24자까지 쓸 수 있습니다.")
		String password,

		@NotBlank(message = "닉네임은 1~20자로 입력해 주세요.")
		@Size(max = 20, message = "닉네임은 1~20자로 입력해 주세요.")
		String nickname,

		// 이메일 인증을 마치고 받은 토큰 (#279). 여러 항목이 틀리면 앞 항목의 이유가 먼저라 맨 뒤에 둔다
		@NotBlank(message = "이메일 인증을 다시 해 주세요.")
		String verificationToken) {

	// 로그나 오류 메시지에 비밀번호가 남지 않게 한다
	@Override
	public String toString() {
				return "SignUpMemberRequest[email=" + email + ", password=****, nickname=" + nickname
				+ ", verificationToken=****]";
	}

}
