package com.pawmento.member.application.port.out;

import com.pawmento.member.domain.Member;

public interface SaveMemberPort {
	Member save(Member member);
}
