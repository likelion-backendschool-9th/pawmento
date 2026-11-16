package com.pawmento.member.application.dto;

import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.MemberRole;

// 회원 정보 응답. 비밀번호 해시는 담지 않는다
public record MemberResponse(Long id, String email, String nickname, MemberRole role) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(), member.getRole());
	}

}
