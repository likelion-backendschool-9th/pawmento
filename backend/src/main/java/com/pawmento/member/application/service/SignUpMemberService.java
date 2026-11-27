package com.pawmento.member.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.DuplicateMemberTagException;
import com.pawmento.member.application.exception.EmailVerificationRequiredException;
import com.pawmento.member.application.port.out.UseEmailVerificationTokenPort;
import com.pawmento.member.domain.VerificationPurpose;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.in.SignUpMemberCommand;
import com.pawmento.member.application.port.in.SignUpMemberUseCase;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.HashPasswordPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SignUpMemberService implements SignUpMemberUseCase {
	private static final int MAX_SAVE_ATTEMPTS = 3;

	private final ExistsMemberPort existsMemberPort;
	private final SaveMemberPort saveMemberPort;
	private final HashPasswordPort passwordHasher;
	private final Clock clock;
	private final MemberTagGenerator tagGenerator;
	private final TransactionTemplate transaction;
	private final UseEmailVerificationTokenPort useVerificationTokenPort;
	private final VerificationCodes codes;

	@Override
	@Transactional(propagation = Propagation.NEVER)
	public MemberResult signUp(SignUpMemberCommand command) {
		String nickname = Member.requireNickname(command.nickname());
		if (existsMemberPort.existsByEmail(Member.normalizeEmail(command.email()))) {
			throw new DuplicateEmailException();
		}
		// #279-S9 토큰이 없으면 비밀번호 해시나 DB 연결 전에 거절한다
		String token = command.verificationToken();
		if (token == null || token.isBlank()) {
			throw new EmailVerificationRequiredException();
		}
		String tokenHash = codes.hash(token);
		// BCrypt는 느려서 한 번만 하고, DB 연결을 잡기 전에 끝낸다
		String passwordHash = passwordHasher.hash(command.rawPassword());
		for (int attempt = 1; ; attempt++) {
			try {
				return transaction.execute(status -> signUpOnce(command.email(), passwordHash, nickname, tokenHash));
			}
			catch (DuplicateMemberTagException e) {
				if (attempt == MAX_SAVE_ATTEMPTS) {
					throw e;
				}
			}
		}
	}

	private MemberResult signUpOnce(String email, String passwordHash, String nickname, String tokenHash) {
		String normalized = Member.normalizeEmail(email);
		if (existsMemberPort.existsByEmail(normalized)) {
			throw new DuplicateEmailException();
		}
		Instant now = Instant.now(clock);
		// #279-S8~S10 가입과 같은 트랜잭션에서 토큰을 쓴다. 이 시도가 롤백되면(태그 충돌 등) 토큰도 되살아난다
		if (!useVerificationTokenPort.use(normalized, VerificationPurpose.SIGNUP, tokenHash, now)) {
			throw new EmailVerificationRequiredException();
		}
		Member member = Member.register(email, passwordHash, nickname, tagGenerator.next(nickname), now);
		return MemberResult.from(saveMemberPort.save(member));
	}
}