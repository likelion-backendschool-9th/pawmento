package com.pawmento.member.domain;

import java.time.Instant;
import java.util.Locale;

import com.pawmento.member.domain.exception.InvalidMemberNicknameException;
import com.pawmento.member.domain.exception.MemberNicknameLimitException;

import lombok.Getter;

// 로그인에 필요한 최소 회원 정보. 소셜 로그인 방법은 SocialAccount에 따로 둔다 (#109)
@Getter
public class Member {

	private static final int NICKNAME_MAX_LENGTH = 20;
	private static final String DEFAULT_NICKNAME = "보호자";
	private static final String TAG_PATTERN = "\\d{4}";

	private Long id;

	private String email;

	// 비밀번호 원문은 저장하지 않는다. 해시만 둔다. 소셜로만 가입한 회원은 null
	private String passwordHash;

	private String nickname;

	// 같은 닉네임을 구분하는 숫자 4자리. 앞자리 0을 지키려고 문자열로 둔다 (#137)
	private String tag;

	private MemberRole role;

	private Instant createdAt;

	// Asia/Seoul 달력월 한도 판정용. 한 번도 안 바꿨으면 null
	private Instant nicknameChangedAt;

	private Member(String email, String passwordHash, String nickname, String tag, MemberRole role, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
		this.tag = tag;
		this.role = role;
		this.createdAt = createdAt;
	}

	public static Member register(String email, String passwordHash, String nickname, String tag, Instant now) {
		if (passwordHash == null || passwordHash.isBlank()) {
			throw new IllegalArgumentException("이메일 가입에는 비밀번호 해시가 필요합니다.");
		}
		return new Member(normalizeEmail(email), passwordHash, requireNickname(nickname), requireTag(tag), MemberRole.USER, now);
	}

	// 소셜 회원 가입. 비밀번호 해시는 받지 않고, 제공자 계정은 저장할 때 SocialAccount로 함께 남긴다
	public static Member registerSocial(String email, String nickname, String tag, Instant now) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("이메일이 없습니다.");
		}
		return new Member(normalizeEmail(email), null, socialNickname(nickname), requireTag(tag), MemberRole.USER, now);
	}

	public static Member restore(Long id, String email, String passwordHash, String nickname, String tag, MemberRole role,
			Instant createdAt, Instant nicknameChangedAt) {
		Member member = new Member(email, passwordHash, nickname, tag, role, createdAt);
		member.id = id;
		member.nicknameChangedAt = nicknameChangedAt;
		return member;
	}

	// 비밀번호로 로그인할 수 있는 회원인가. 가입 방식이 아니라 비밀번호가 있는지로 정한다 (#109-R2)
	public boolean hasPassword() {
		return passwordHash != null;
	}

	public void changeNickname(String nickname, String newTag, Instant now, java.time.ZoneId zone) {
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
		this.tag = requireTag(newTag);
		this.nicknameChangedAt = now;
	}

	public static String requireNickname(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			throw new InvalidMemberNicknameException();
		}
		String trimmed = nickname.strip();
		if (trimmed.isEmpty() || trimmed.length() > NICKNAME_MAX_LENGTH) {
			throw new InvalidMemberNicknameException();
		}
		return trimmed;
	}

	// 같은 이메일이 대소문자·공백 차이로 두 번 가입되지 않게, 저장과 조회에 같은 형태를 쓴다
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public static String socialNickname(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return DEFAULT_NICKNAME;
		}
		String stripped = nickname.strip();
		if (stripped.length() > NICKNAME_MAX_LENGTH) {
			return stripped.substring(0, NICKNAME_MAX_LENGTH);
		}
		return stripped;
	}

	private static String requireTag(String tag) {
		if (tag == null || !tag.matches(TAG_PATTERN)) {
			throw new IllegalArgumentException("태그는 숫자 4자리입니다.");
		}
		return tag;
	}
}
