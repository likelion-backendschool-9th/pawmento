package com.pawmento.member.application.port.out;

public interface ExistsMemberPort {

	// email은 Member.normalizeEmail을 거친 값이어야 한다
	boolean existsByEmail(String email);

}
