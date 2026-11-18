package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.member.application.exception.InvalidNicknameException;
import com.pawmento.member.application.exception.NicknameChangeLimitException;
import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.Member;

class ChangeMemberNicknameServiceTest {
	private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final LoadMemberPort load = mock(LoadMemberPort.class);
	private final SaveMemberPort save = mock(SaveMemberPort.class);
	private final ChangeMemberNicknameService service = new ChangeMemberNicknameService(load, save,
			Clock.fixed(NOW, SEOUL));

	@Test
	@DisplayName("#52-S3 도메인의 입력 오류만 애플리케이션 입력 예외로 변환한다")
	void invalidNicknameIsTranslated() {
		Member member = Member.register("a@example.com", "hash", "보호자", NOW);
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member));
		assertThatThrownBy(() -> service.changeNickname(new ChangeMemberNicknameCommand(1L, " ")))
				.isInstanceOf(InvalidNicknameException.class);
	}

	@Test
	@DisplayName("#52-S4 도메인의 월 한도만 한도 예외로 변환한다")
	void limitIsTranslated() {
		Member member = Member.register("a@example.com", "hash", "보호자", NOW);
		member.changeNickname("첫번째", NOW, SEOUL);
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member));
		assertThatThrownBy(() -> service.changeNickname(new ChangeMemberNicknameCommand(1L, "두번째")))
				.isInstanceOf(NicknameChangeLimitException.class);
	}

	@Test
	@DisplayName("#52-S3 저장소 장애를 닉네임 한도 오류로 오인하지 않는다")
	void infrastructureFailureIsNotBusinessError() {
		Member member = Member.register("a@example.com", "hash", "보호자", NOW);
		when(load.loadForNicknameChange(1L)).thenReturn(Optional.of(member));
		IllegalStateException failure = new IllegalStateException("storage unavailable");
		when(save.save(any())).thenThrow(failure);
		assertThatThrownBy(() -> service.changeNickname(new ChangeMemberNicknameCommand(1L, "콩이 보호자")))
				.isSameAs(failure);
	}
}
