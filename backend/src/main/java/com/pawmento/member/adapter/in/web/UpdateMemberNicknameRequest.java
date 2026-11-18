package com.pawmento.member.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemberNicknameRequest(
		@NotBlank(message = "닉네임은 1~20자로 입력해 주세요.")
		@Size(max = 20, message = "닉네임은 1~20자로 입력해 주세요.")
		String nickname) {
}
