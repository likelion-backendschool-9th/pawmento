package com.pawmento.member.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberResult;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetMemberService implements GetMemberUseCase {

	private final LoadMemberPort loadMemberPort;

	@Override
	@Transactional(readOnly = true)
	public GetMemberResult get(GetMemberQuery query) {
		Member member = loadMemberPort.loadById(query.memberId()).orElseThrow(MemberNotFoundException::new);
		return new GetMemberResult(member.getId(), member.getEmail(), member.getNickname(), member.getRole().name());
	}

}
