package com.pawmento.member.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.DuplicateMemberTagException;
import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.in.ChangeMemberNicknameUseCase;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChangeMemberNicknameService implements ChangeMemberNicknameUseCase {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final int MAX_SAVE_ATTEMPTS = 3;

	private final LoadMemberPort loadMemberPort;
	private final SaveMemberPort saveMemberPort;
	private final Clock clock;
	private final MemberTagGenerator tagGenerator;
	private final TransactionTemplate transaction;

	// 태그 충돌은 실패한 트랜잭션 안에서 다시 시도할 수 없어, 메서드에는 트랜잭션을 걸지 않는다.
	// 한 번의 시도(회원 잠금 조회 → 같은 닉네임·한도 확인 → 태그 발급 → 저장)를 새 트랜잭션으로 실행하고,
	// 지면 그 시도만 롤백한 뒤 회원을 다시 읽어 처음부터 한다 (#137-S7, S11)
	@Override
	@Transactional(propagation = Propagation.NEVER)
	public MemberResult changeNickname(ChangeMemberNicknameCommand command) {
		for (int attempt = 1; ; attempt++) {
			try {
				return transaction.execute(status -> changeOnce(command.memberId(), command.nickname()));
			}
			catch (DuplicateMemberTagException e) {
				if (attempt == MAX_SAVE_ATTEMPTS) {
					throw e;
				}
			}
		}
	}

	private MemberResult changeOnce(Long memberId, String requestedNickname) {
		Member member = loadMemberPort.loadForNicknameChange(memberId)
				.orElseThrow(MemberNotFoundException::new);
		String nickname = Member.requireNickname(requestedNickname);
		if (nickname.equals(member.getNickname())) {
			// 같은 닉네임이면 태그도 한도도 그대로라 저장할 것이 없다 (#137-S5)
			return MemberResult.from(member);
		}
		Instant now = Instant.now(clock);
		member.requireNicknameChangeAllowed(now, SEOUL);
		String newTag = tagGenerator.next(nickname, member.getTag());
		member.changeNickname(nickname, newTag, Instant.now(clock), SEOUL);
		return MemberResult.from(saveMemberPort.save(member));
	}
}