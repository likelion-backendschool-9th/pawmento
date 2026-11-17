package com.pawmento.member.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.InvalidCredentialsException;
import com.pawmento.member.application.port.in.LoginMemberCommand;
import com.pawmento.member.application.port.in.LoginMemberResult;
import com.pawmento.member.application.port.in.LoginMemberUseCase;
import com.pawmento.member.application.port.out.HashPasswordPort;
import com.pawmento.member.application.port.out.IssueTokenPort;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.domain.Member;

@Service
public class LoginMemberService implements LoginMemberUseCase {

	private final LoadMemberPort loadMemberPort;

	private final HashPasswordPort hashPasswordPort;

	private final IssueTokenPort issueTokenPort;

	// 없는 이메일로 로그인할 때 비교할 해시. 가입된 이메일일 때와 응답 시간이 비슷해지게 한다
	private final String absentMemberPasswordHash;

	// 해시를 미리 만들어 두어야 해서 롬복 대신 생성자를 직접 쓴다
	public LoginMemberService(LoadMemberPort loadMemberPort, HashPasswordPort hashPasswordPort,
			IssueTokenPort issueTokenPort) {
		this.loadMemberPort = loadMemberPort;
		this.hashPasswordPort = hashPasswordPort;
		this.issueTokenPort = issueTokenPort;
		this.absentMemberPasswordHash = hashPasswordPort.hash("absent-member-password");
	}

	@Override
	@Transactional(readOnly = true)
	public LoginMemberResult login(LoginMemberCommand command) {
		Member member = loadMemberPort.loadByEmail(Member.normalizeEmail(command.email())).orElse(null);
		if (member == null) {
			hashPasswordPort.matches(command.rawPassword(), absentMemberPasswordHash);
			throw new InvalidCredentialsException();
		}
		if (!hashPasswordPort.matches(command.rawPassword(), member.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return new LoginMemberResult(issueTokenPort.issue(member.getId(), member.getRole()), member.getId(),
				member.getEmail(), member.getNickname(), member.getRole().name());
	}

}
