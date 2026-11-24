package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.pawmento.member.application.exception.DuplicateMemberTagException;
import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.in.MemberResult;
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

	@Test
	@DisplayName("#137-S4 닉네임이 바뀌면 이전 태그를 피해 새 태그를 받는다")
	void newNicknameGetsNewTag() {
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member()));
		when(tags.next("보리 보호자", "0427")).thenReturn("0581");

		MemberResult result = service.changeNickname(new ChangeMemberNicknameCommand(1L, "보리 보호자"));

		assertThat(result.nickname()).isEqualTo("보리 보호자");
		assertThat(result.tag()).isEqualTo("0581");
	}

	@Test
	@DisplayName("#137-S5 같은 닉네임이면 태그를 뽑지도 저장하지도 않는다")
	void sameNicknameKeepsTag() {
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member()));

		MemberResult result = service.changeNickname(new ChangeMemberNicknameCommand(1L, " 보호자 "));

		assertThat(result.tag()).isEqualTo("0427");
		verify(tags, never()).next(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#137-S7 저장이 태그 충돌로 지면 회원을 다시 읽어 새 태그로 다시 저장한다")
	void retriesWithFreshMemberOnTagConflict() {
		// 시도마다 새 트랜잭션에서 다시 읽으므로, 실패한 시도의 변경이 없는 회원을 돌려준다
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member()), Optional.of(member()));
		when(tags.next("보리", "0427")).thenReturn("0581", "0700");
		when(save.save(any()))
				.thenThrow(new DuplicateMemberTagException())
				.thenAnswer(invocation -> invocation.getArgument(0));

		MemberResult result = service.changeNickname(new ChangeMemberNicknameCommand(1L, "보리"));

		assertThat(result.tag()).isEqualTo("0700");
		verify(transaction, times(2)).execute(any());
		verify(load, times(2)).loadForNicknameChange(1L);
	}

	@Test
	@DisplayName("#137-S11 세 번 모두 태그 충돌이면 409 예외를 그대로 올린다")
	void givesUpAfterThreeConflicts() {
		// 시도마다 롤백된 새 회원을 읽는 것처럼, 부를 때마다 새 회원을 돌려준다
		when(load.loadForNicknameChange(1L)).thenAnswer(invocation -> Optional.of(member()));
		when(tags.next("보리", "0427")).thenReturn("0581");
		when(save.save(any())).thenThrow(new DuplicateMemberTagException());

		assertThatThrownBy(() -> service.changeNickname(new ChangeMemberNicknameCommand(1L, "보리")))
				.isInstanceOf(DuplicateMemberTagException.class);
		verify(transaction, times(3)).execute(any());
	}

	private Member member() {
		return Member.register("a@example.com", "hash", "보호자", "0427", NOW);
	}
}
