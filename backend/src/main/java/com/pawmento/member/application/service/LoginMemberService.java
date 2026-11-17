package com.pawmento.member.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.InvalidCredentialsException;
import com.pawmento.member.application.port.in.LoginMemberCommand;
import com.pawmento.member.application.port.in.LoginMemberResult;
import com.pawmento.member.application.port.in.LoginMemberUseCase;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.PasswordHasher;
import com.pawmento.member.application.port.out.TokenIssuer;
import com.pawmento.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginMemberService implements LoginMemberUseCase {
	private final LoadMemberPort loadMemberPort;
	private final PasswordHasher passwordHasher;
	private final TokenIssuer tokenIssuer;

	@Override
	@Transactional(readOnly = true)
	public LoginMemberResult login(LoginMemberCommand command) {
		Member member = loadMemberPort.findByEmail(Member.normalizeEmail(command.email())).orElse(null);
		if (member == null) {
			passwordHasher.matches(command.rawPassword(), null);
			throw new InvalidCredentialsException();
		}
		if (!passwordHasher.matches(command.rawPassword(), member.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return new LoginMemberResult(tokenIssuer.issue(member.getId(), member.getRole()), MemberResult.from(member));
	}
}
