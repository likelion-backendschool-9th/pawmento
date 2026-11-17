package com.pawmento.member.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.InvalidNicknameException;
import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.exception.NicknameChangeLimitException;
import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.in.ChangeMemberNicknameUseCase;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.InvalidMemberNicknameException;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.MemberNicknameLimitException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChangeMemberNicknameService implements ChangeMemberNicknameUseCase {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final LoadMemberPort loadMemberPort;
	private final SaveMemberPort saveMemberPort;
	private final Clock clock;

	@Override
	@Transactional
	public MemberResult changeNickname(ChangeMemberNicknameCommand command) {
		Member member = loadMemberPort.findForNicknameChange(command.memberId())
				.orElseThrow(MemberNotFoundException::new);
		try {
			member.changeNickname(command.nickname(), Instant.now(clock), SEOUL);
		}
		catch (InvalidMemberNicknameException e) {
			throw new InvalidNicknameException(e.getMessage());
		}
		catch (MemberNicknameLimitException e) {
			throw new NicknameChangeLimitException();
		}
		return MemberResult.from(saveMemberPort.save(member));
	}
}
