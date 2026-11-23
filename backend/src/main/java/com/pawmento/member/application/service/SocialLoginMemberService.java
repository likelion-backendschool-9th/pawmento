package com.pawmento.member.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.DuplicateSocialAccountException;
import com.pawmento.member.application.exception.SocialEmailConflictException;
import com.pawmento.member.application.exception.SocialLoginFailedException;
import com.pawmento.member.application.exception.UnsupportedSocialProviderException;
import com.pawmento.member.application.port.in.LoginMemberResult;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.in.SocialLoginMemberCommand;
import com.pawmento.member.application.port.in.SocialLoginMemberUseCase;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.IssueTokenPort;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.LoadSocialProfilePort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.application.port.out.SocialProfile;
import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SocialLoginMemberService implements SocialLoginMemberUseCase {

	private final LoadSocialProfilePort loadSocialProfilePort;

	private final LoadMemberPort loadMemberPort;

	private final ExistsMemberPort existsMemberPort;

	private final SaveMemberPort saveMemberPort;

	private final IssueTokenPort issueTokenPort;

	private final Clock clock;

	// 제공자 HTTP 호출이 끼어 있어 메서드 전체에 @Transactional을 걸지 않는다.
	// 외부 응답을 기다리는 동안 DB 연결을 붙잡지 않기 위해서다
	@Override
	public LoginMemberResult login(SocialLoginMemberCommand command) {
		Optional<AuthProvider> found = AuthProvider.findSocial(command.provider());
		if (found.isEmpty()) {
			throw new UnsupportedSocialProviderException();
		}
		AuthProvider provider = found.get();

		SocialProfile profile = loadSocialProfilePort.load(provider, command.code(), command.state(),
				command.redirectUri());

		Optional<Member> existing = loadMemberPort.loadByProvider(provider, profile.providerUserId());
		Member member;
		if (existing.isPresent()) {
			member = existing.get();
		}
		else {
			member = register(profile);
		}

		String accessToken = issueTokenPort.issue(member.getId(), member.getRole());
		return new LoginMemberResult(accessToken, MemberResult.from(member));
	}

	private Member register(SocialProfile profile) {
		if (profile.email() == null || profile.email().isBlank()) {
			throw new SocialLoginFailedException("이메일 제공에 동의해야 가입할 수 있습니다.");
		}
		// 같은 이메일의 회원이 있어도, 같은 소셜 계정의 다른 요청이 방금 가입한 것일 수 있다 (#40-S15).
		// 그 계정이면 그 회원으로 로그인하고, 아니면 다른 방법으로 가입된 이메일이므로 409 (계정 선점 방지)
		if (existsMemberPort.existsByEmail(Member.normalizeEmail(profile.email()))) {
			return loadMemberPort.loadByProvider(profile.provider(), profile.providerUserId())
					.orElseThrow(SocialEmailConflictException::new);
		}
		Member member = Member.registerSocial(profile.email(), profile.nickname(), Instant.now(clock));
		try {
			return saveMemberPort.saveSocial(member, profile.provider(), profile.providerUserId());
		}
		catch (DuplicateEmailException | DuplicateSocialAccountException e) {
			// 동시에 온 같은 소셜 계정의 첫 로그인이면 먼저 저장된 회원으로 로그인한다
			// 그 계정이 없으면 다른 방법으로 같은 이메일이 먼저 가입한 것이므로 그대로 409
			return loadMemberPort.loadByProvider(profile.provider(), profile.providerUserId())
					.orElseThrow(() -> e);
		}
	}

}