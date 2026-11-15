package com.pawmento.member.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.member.application.dto.MemberResponse;
import com.pawmento.member.application.port.in.MemberUseCase;

@RestController
@RequestMapping("/api/members")
class MemberController {

	private final MemberUseCase memberUseCase;

	MemberController(MemberUseCase memberUseCase) {
		this.memberUseCase = memberUseCase;
	}

	@GetMapping("/me")
	MemberResponse me(@LoginMember Long memberId) {
		return memberUseCase.getMember(memberId);
	}

}
