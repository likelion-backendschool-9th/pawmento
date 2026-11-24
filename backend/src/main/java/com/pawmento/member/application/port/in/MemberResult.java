package com.pawmento.member.application.port.in;

import com.pawmento.member.domain.Member;

public record MemberResult(Long id, String email, String nickname, String tag, String role) {
	public static MemberResult from(Member member) {
		return new MemberResult(member.getId(), member.getEmail(), member.getNickname(), member.getTag(), member.getRole().name());
	}
}
