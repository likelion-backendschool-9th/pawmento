package com.pawmento.member.domain;

import java.time.Instant;
import java.util.Locale;

import com.pawmento.member.domain.exception.InvalidMemberNicknameException;
import com.pawmento.member.domain.exception.MemberNicknameLimitException;

import lombok.Getter;

// 로그인에 필요한 최소 회원 정보. 프로필 등 나머지 필드는 스프린트 1의 member 설계에서 더한다
@Getter
public class Member {

	private Long id;

	private String email;

	// 비밀번호 원문은 저장하지 않는다. 해시만 둔다
	private String passwordHash;

	private String nickname;

	private MemberRole role;

	private Instant createdAt;

	// Asia/Seoul 달력월 한도 판정용. 한 번도 안 바꿨으면 null
	private Instant nicknameChangedAt;

	private Member(String email, String passwordHash, String nickname, MemberRole role, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
		this.role = role;
		this.createdAt = createdAt;
	}

	public static Member register(String email, String passwordHash, String nickname, Instant now) {
		return new Member(normalizeEmail(email), passwordHash, requireNickname(nickname), MemberRole.USER, now);
	}

	public static Member restore(Long id, String email, String passwordHash, String nickname, MemberRole role,
			Instant createdAt, Instant nicknameChangedAt) {
		Member member = new Member(email, passwordHash, nickname, role, createdAt);
		member.id = id;
		member.nicknameChangedAt = nicknameChangedAt;
		return member;
	}

	public void changeNickname(String nickname, Instant now, java.time.ZoneId zone) {
		String trimmed = requireNickname(nickname);
		if (trimmed.equals(this.nickname)) {
			return;
		}
		if (nicknameChangedAt != null) {
			java.time.YearMonth last = java.time.YearMonth.from(nicknameChangedAt.atZone(zone));
			java.time.YearMonth current = java.time.YearMonth.from(now.atZone(zone));
			if (last.equals(current)) {
				throw new MemberNicknameLimitException();
			}
		}
		this.nickname = trimmed;
		this.nicknameChangedAt = now;
	}

	private static String requireNickname(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			throw new InvalidMemberNicknameException();
		}
		String trimmed = nickname.strip();
		if (trimmed.isEmpty() || trimmed.length() > 20) {
			throw new InvalidMemberNicknameException();
		}
		return trimmed;
	}

	// 같은 이메일이 대소문자·공백 차이로 두 번 가입되지 않게, 저장과 조회에 같은 형태를 쓴다
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
