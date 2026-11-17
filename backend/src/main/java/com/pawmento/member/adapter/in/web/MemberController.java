package com.pawmento.member.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberResult;
import com.pawmento.member.application.port.in.GetMemberUseCase;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
class MemberController {

	private final GetMemberUseCase getMemberUseCase;

	@GetMapping("/me")
	MemberResponse me(@LoginMember Long memberId) {
		GetMemberResult result = getMemberUseCase.get(new GetMemberQuery(memberId));
		return new MemberResponse(result.id(), result.email(), result.nickname(), result.role());
	}

}
