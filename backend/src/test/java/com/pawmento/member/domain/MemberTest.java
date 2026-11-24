package com.pawmento.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.member.domain.exception.InvalidMemberNicknameException;
import com.pawmento.member.domain.exception.MemberNicknameLimitException;

class MemberTest {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");

	@Test
	@DisplayName("#52-S3 같은 닉네임 저장은 한도를 소비하지 않고 실제 변경은 한 번만 허용한다")
	void unchangedNicknameDoesNotConsumeLimit() {
		Member member = Member.register(" A@EXAMPLE.COM ", "hash", "보호자","0427", NOW);
		member.changeNickname(" 보호자 ", "0581", NOW, SEOUL);
		assertThat(member.getNicknameChangedAt()).isNull();
		member.changeNickname("콩이 보호자", "0581", NOW, SEOUL);
		assertThat(member.getEmail()).isEqualTo("a@example.com");
		assertThat(member.getNicknameChangedAt()).isEqualTo(NOW);
		assertThatThrownBy(() -> member.changeNickname("보리 보호자", "0582", NOW, SEOUL))
				.isInstanceOf(MemberNicknameLimitException.class);
		assertThat(member.getNickname()).isEqualTo("콩이 보호자");
	}

	@Test
	@DisplayName("#52-S3 잘못된 닉네임은 전용 예외를 던지고 상태를 바꾸지 않는다")
	void invalidNicknameKeepsOriginalState() {
		Member member = Member.register("a@example.com", "hash", "보호자", "0427", NOW);
		for (String nickname : new String[] { null, " ", "가".repeat(21) }) {
			assertThatThrownBy(() -> member.changeNickname(nickname, "0581", NOW, SEOUL))
					.isInstanceOf(InvalidMemberNicknameException.class);
		}
		assertThat(member.getNickname()).isEqualTo("보호자");
		assertThat(member.getNicknameChangedAt()).isNull();
	}

	@Test
	@DisplayName("#52-S5 UTC 날짜가 같아도 서울 달력월이 바뀌면 변경을 허용한다")
	void seoulMonthBoundary() {
		Member member = Member.register("a@example.com", "hash", "보호자", "0427", NOW);
		member.changeNickname("11월 보호자", "0581", Instant.parse("2026-11-30T14:59:00Z"), SEOUL);
		member.changeNickname("12월 보호자", "0582", Instant.parse("2026-11-30T15:00:00Z"), SEOUL);
		assertThat(member.getNickname()).isEqualTo("12월 보호자");
	}

	@Test
	@DisplayName("#109-R2 비밀번호 로그인 가능 여부는 가입 방식이 아니라 비밀번호가 있는지로 정한다")
	void hasPasswordDependsOnPasswordHash() {
		Member emailMember = Member.register("a@example.com", "hash", "보호자", "0427", NOW);
		Member socialMember = Member.registerSocial("b@example.com", "보호자","0427", NOW);

		assertThat(emailMember.hasPassword()).isTrue();
		assertThat(socialMember.hasPassword()).isFalse();
		assertThatThrownBy(() -> Member.register("c@example.com", null, "보호자","0427", NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
