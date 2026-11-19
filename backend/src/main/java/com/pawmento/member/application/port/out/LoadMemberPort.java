package com.pawmento.member.application.port.out;

import java.util.Optional;

import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;

public interface LoadMemberPort {
	Optional<Member> loadById(Long id);

	Optional<Member> loadByEmail(String email);

	// 변경 확정까지 같은 회원의 닉네임 변경이 경합하지 않도록 조회한다.
	Optional<Member> loadForNicknameChange(Long memberId);

		// 소셜 회원을 제공자와 제공자 사용자 id로 찾는다
	Optional<Member> loadByProvider(AuthProvider provider, String providerUserId);

}
