package com.pawmento.member.application.port.out;

import java.util.Optional;

import com.pawmento.global.security.AuthProperties;
import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;

public interface LoadMemberPort {

	Optional<Member> loadById(Long id);

	// email은 Member.normalizeEmail을 거친 값이어야 한다
	Optional<Member> loadByEmail(String email);

	// 소셜 회원을 제공자와 제공자 사용자 id로 찾는다
	Optional<Member> loadByProvider(AuthProvider provider, String providerUserId);

}
