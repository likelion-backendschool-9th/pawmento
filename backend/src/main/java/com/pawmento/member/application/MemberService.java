package com.pawmento.member.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.dto.LoginResult;
import com.pawmento.member.application.dto.MemberResponse;
import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.InvalidCredentialsException;
import com.pawmento.member.application.exception.InvalidNicknameException;
import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.exception.NicknameChangeLimitException;
import com.pawmento.member.application.port.in.MemberUseCase;
import com.pawmento.member.application.port.out.MemberRepository;
import com.pawmento.member.application.port.out.PasswordHasher;
import com.pawmento.member.application.port.out.TokenIssuer;
import com.pawmento.member.domain.Member;

@Service
@Transactional(readOnly = true)
class MemberService implements MemberUseCase {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final MemberRepository memberRepository;

	private final PasswordHasher passwordHasher;

	private final TokenIssuer tokenIssuer;

	private final Clock clock;

	// 없는 이메일로 로그인할 때 비교할 해시. 가입된 이메일일 때와 응답 시간이 비슷해지게 한다
	private final String absentMemberPasswordHash;

	MemberService(MemberRepository memberRepository, PasswordHasher passwordHasher, TokenIssuer tokenIssuer,
			Clock clock) {
		this.memberRepository = memberRepository;
		this.passwordHasher = passwordHasher;
		this.tokenIssuer = tokenIssuer;
		this.clock = clock;
		this.absentMemberPasswordHash = passwordHasher.hash("absent-member-password");
	}

	@Override
	@Transactional
	public MemberResponse signUp(String email, String rawPassword, String nickname) {
		if (memberRepository.existsByEmail(Member.normalizeEmail(email))) {
			throw new DuplicateEmailException();
		}
		Member member = Member.register(email, passwordHasher.hash(rawPassword), nickname, Instant.now(clock));
		return MemberResponse.from(memberRepository.save(member));
	}

	@Override
	public LoginResult login(String email, String rawPassword) {
		Member member = memberRepository.findByEmail(Member.normalizeEmail(email)).orElse(null);
		if (member == null) {
			passwordHasher.matches(rawPassword, absentMemberPasswordHash);
			throw new InvalidCredentialsException();
		}
		if (!passwordHasher.matches(rawPassword, member.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return new LoginResult(tokenIssuer.issue(member.getId(), member.getRole()), MemberResponse.from(member));
	}

	@Override
	public MemberResponse getMember(Long memberId) {
		return memberRepository.findById(memberId)
			.map(MemberResponse::from)
			.orElseThrow(MemberNotFoundException::new);
	}

	@Override
	@Transactional
	public MemberResponse changeNickname(Long memberId, String nickname) {
		Member member = memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
		try {
			member.changeNickname(nickname, Instant.now(clock), SEOUL);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidNicknameException(e.getMessage());
		}
		catch (IllegalStateException e) {
			throw new NicknameChangeLimitException();
		}
		return MemberResponse.from(memberRepository.save(member));
	}

}
