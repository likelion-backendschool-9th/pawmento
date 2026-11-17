package com.pawmento.member.domain;

import java.time.Instant;
import java.util.Locale;

import lombok.Getter;

// 로그인에 필요한 최소 회원 정보. 프로필 등 나머지 필드는 스프린트 1의 member 설계에서 더한다
@Getter
public class Member {

	private static final int NICKNAME_MAX_LENGTH = 20;
	private static final String DEFAULT_NICKNAME = "보호자";

	private Long id;

	private String email;

	// 비밀번호 원문은 저장하지 않는다. 해시만 둔다
	private String passwordHash;

	private String nickname;

	private MemberRole role;

	private AuthProvider provider;

	private String providerUserId;

	private Instant createdAt;

	private Member(Long id, String email, String passwordHash, String nickname, MemberRole role, AuthProvider provider, String providerUserId, Instant createdAt) {
		this.id = id;
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
		this.role = role;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.createdAt = createdAt;
	}

	// 새 회원. id는 DB에 저장한 뒤 생기므로 null이다
	public static Member register(String email, String passwordHash, String nickname, Instant now) {
		return new Member(null, normalizeEmail(email), passwordHash, nickname.strip(), MemberRole.USER, AuthProvider.LOCAL, null,  now);
	}

	public static Member registerSocial (AuthProvider provider, String providerUserId, String email, String nickname, Instant now) {
		if (provider == null || provider == AuthProvider.LOCAL) {
			throw new IllegalArgumentException("소셜 가입에는 소셜 제공자가 필요합니다.");
		}
		if (providerUserId == null || providerUserId.isBlank()) {
			throw new IllegalArgumentException("제공자 사용자 id가 없습니다.");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("이메일이 없습니다.");
		}
		return new Member(null, normalizeEmail(email), null, socialNickname(nickname), MemberRole.USER, provider, providerUserId, now);
	}

	// 저장된 값을 도메인 객체로 되살린다. 이미 규칙을 거친 값이므로 다시 검사하지 않는다
	public static Member restore(Long id, String email, String passwordHash, String nickname, MemberRole role, AuthProvider provider, String providerUserId,
			Instant createdAt) {
		return new Member(id, email, passwordHash, nickname, role, provider, providerUserId, createdAt);
	}

	public boolean isLocal() {
		return provider == AuthProvider.LOCAL;
	}

	// 같은 이메일이 대소문자·공백 차이로 두 번 가입되지 않게, 저장과 조회에 같은 형태를 쓴다
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static String socialNickname(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return DEFAULT_NICKNAME;
		}
		String stripped = nickname.strip();
		if (stripped.length() > NICKNAME_MAX_LENGTH) {
			return stripped.substring(0, NICKNAME_MAX_LENGTH);
		}
		return stripped;
	}

}
