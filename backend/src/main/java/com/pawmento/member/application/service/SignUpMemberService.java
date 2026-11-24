package com.pawmento.member.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.DuplicateMemberTagException;
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

	// 태그 충돌은 실패한 트랜잭션 안에서 다시 시도할 수 없어, 메서드에는 트랜잭션을 걸지 않는다.
	// 한 번의 시도를 새 트랜잭션으로 실행하고, 지면 그 시도만 롤백한 뒤 처음부터 다시 한다 (#137-S10)
	@Override
	@Transactional(propagation = Propagation.NEVER)
	public MemberResult signUp(SignUpMemberCommand command) {
		String nickname = Member.requireNickname(command.nickname());
		if (existsMemberPort.existsByEmail(Member.normalizeEmail(command.email()))) {
			throw new DuplicateEmailException();
		}
		// BCrypt는 느려서 한 번만 하고, DB 연결을 잡기 전에 끝낸다
		String passwordHash = passwordHasher.hash(command.rawPassword());
		for (int attempt = 1; ; attempt++) {
			try {
				return transaction.execute(status -> signUpOnce(command.email(), passwordHash, nickname));
			}
			catch (DuplicateMemberTagException e) {
				if (attempt == MAX_SAVE_ATTEMPTS) {
					throw e;
				}
			}
		}
	}

	private MemberResult signUpOnce(String email, String passwordHash, String nickname) {
		if (existsMemberPort.existsByEmail(Member.normalizeEmail(email))) {
			throw new DuplicateEmailException();
		}
		Member member = Member.register(email, passwordHash, nickname, tagGenerator.next(nickname), Instant.now(clock));
		return MemberResult.from(saveMemberPort.save(member));
	}
}