package com.pawmento.member.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.member.adapter.in.web.dto.UpdateNicknameRequest;
import com.pawmento.member.application.dto.MemberResponse;
import com.pawmento.member.application.port.in.MemberUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/members")
class MemberController {

	private final MemberUseCase memberUseCase;

	MemberController(MemberUseCase memberUseCase) {
		this.memberUseCase = memberUseCase;
	}

	@GetMapping("/me")
	MemberResponse me(@LoginMember Long memberId) {
		return memberUseCase.getMember(memberId);
	}

	@PatchMapping("/me")
	MemberResponse changeNickname(@LoginMember Long memberId, @Valid @RequestBody UpdateNicknameRequest request) {
		return memberUseCase.changeNickname(memberId, request.nickname());
	}

}
