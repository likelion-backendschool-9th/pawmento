package com.pawmento.member.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.in.ChangeMemberNicknameUseCase;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
class MemberController {
	private final GetMemberUseCase getMemberUseCase;
	private final ChangeMemberNicknameUseCase changeMemberNicknameUseCase;

	@GetMapping("/me")
	MemberResponse me(@LoginMember Long memberId) {
		return MemberResponse.from(getMemberUseCase.get(new GetMemberQuery(memberId)));
	}

	@PatchMapping("/me")
	MemberResponse changeNickname(@LoginMember Long memberId, @Valid @RequestBody UpdateMemberNicknameRequest request) {
		return MemberResponse.from(changeMemberNicknameUseCase
				.changeNickname(new ChangeMemberNicknameCommand(memberId, request.nickname())));
	}
}
