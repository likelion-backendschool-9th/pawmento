package com.pawmento.member.application.port.out;

import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;

public interface SaveMemberPort {
	Member save(Member member);

	Member saveSocial(Member member, AuthProvider provider, String providerUserId);
}
