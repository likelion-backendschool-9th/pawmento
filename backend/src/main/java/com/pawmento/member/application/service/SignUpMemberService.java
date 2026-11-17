package com.pawmento.member.application.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.port.in.SignUpMemberCommand;
import com.pawmento.member.application.port.in.SignUpMemberResult;
import com.pawmento.member.application.port.in.SignUpMemberUseCase;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.HashPasswordPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SignUpMemberService implements SignUpMemberUseCase {

	private final ExistsMemberPort existsMemberPort;

	private final SaveMemberPort saveMemberPort;

	private final HashPasswordPort hashPasswordPort;

	@Override
	@Transactional
	public SignUpMemberResult signUp(SignUpMemberCommand command) {
		if (existsMemberPort.existsByEmail(Member.normalizeEmail(command.email()))) {
			throw new DuplicateEmailException();
		}
		Member member = Member.register(command.email(), hashPasswordPort.hash(command.rawPassword()),
				command.nickname(), Instant.now());
		Member saved = saveMemberPort.save(member);
		return new SignUpMemberResult(saved.getId(), saved.getEmail(), saved.getNickname(), saved.getRole().name());
	}

}
