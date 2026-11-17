package com.pawmento.member.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.out.LoadMemberPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetMemberService implements GetMemberUseCase {
	private final LoadMemberPort loadMemberPort;

	@Override
	@Transactional(readOnly = true)
	public MemberResult get(GetMemberQuery query) {
		return loadMemberPort.findById(query.memberId()).map(MemberResult::from)
				.orElseThrow(MemberNotFoundException::new);
	}
}
