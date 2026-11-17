package com.pawmento.member.adapter.in.web;

import com.pawmento.member.application.port.in.MemberResult;

public record MemberResponse(Long id, String email, String nickname, String role) {
	static MemberResponse from(MemberResult result) {
		return new MemberResponse(result.id(), result.email(), result.nickname(), result.role());
	}
}
