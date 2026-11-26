package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.exception.InvalidMemberNicknameException;
import com.pawmento.member.domain.exception.MemberNicknameLimitException;

class ChangeMemberNicknameServiceTest {
	private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final LoadMemberPort load = mock(LoadMemberPort.class);
	private final SaveMemberPort save = mock(SaveMemberPort.class);
	private final MemberTagGenerator tags = mock(MemberTagGenerator.class);
	// 실제 트랜잭션 없이, 넘겨받은 한 번의 시도를 그대로 실행하는 가짜
	private final TransactionTemplate transaction = mock(TransactionTemplate.class);
	private final ChangeMemberNicknameService service = new ChangeMemberNicknameService(load, save,
			Clock.fixed(NOW, SEOUL), tags, transaction);

	@BeforeEach
	void runAttemptsDirectly() {
		when(transaction.execute(any())).thenAnswer(
				invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
		when(save.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("#52-S3 도메인의 닉네임 규칙 위반을 바꾸지 않고 그대로 올린다")
	void invalidNicknamePropagates() {
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member()));
		assertThatThrownBy(() -> service.changeNickname(new ChangeMemberNicknameCommand(1L, " ")))
				.isInstanceOf(InvalidMemberNicknameException.class);
	}

	@Test
	@DisplayName("#52-S4 #137 한 달 한도를 쓴 회원은 태그를 뽑기 전에 한도 예외를 그대로 받는다")
	void limitPropagatesBeforeTag() {
		Member member = member();
		member.changeNickname("첫번째", "0581", NOW, SEOUL);
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member));
		assertThatThrownBy(() -> service.changeNickname(new ChangeMemberNicknameCommand(1L, "두번째")))
				.isInstanceOf(MemberNicknameLimitException.class);
		verify(tags, never()).next(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#52-S3 저장소 장애를 닉네임 한도 오류로 오인하지 않는다")
	void infrastructureFailureIsNotBusinessError() {
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member()));
		when(tags.next("콩이 보호자", "0427")).thenReturn("0581");
		IllegalStateException failure = new IllegalStateException("storage unavailable");
		when(save.save(any())).thenThrow(failure);
		assertThatThrownBy(() -> service.changeNickname(new ChangeMemberNicknameCommand(1L, "콩이 보호자")))
				.isSameAs(failure);
	}

	private Member member() {
		return Member.register("a@example.com", "hash", "보호자", "0427", NOW);
	}
}
