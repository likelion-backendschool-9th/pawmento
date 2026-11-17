package com.pawmento.member.application.port.out;

import java.util.Optional;

import com.pawmento.member.domain.Member;

public interface LoadMemberPort {

	Optional<Member> loadById(Long id);

	// email은 Member.normalizeEmail을 거친 값이어야 한다
	Optional<Member> loadByEmail(String email);

}
