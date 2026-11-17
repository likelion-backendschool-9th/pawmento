package com.pawmento.member.domain;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// 로그인에 필요한 최소 회원 정보. 프로필 등 나머지 필드는 스프린트 1의 member 설계에서 더한다
@Entity
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = Member.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"))
public class Member {

	public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254)
	private String email;

	// 비밀번호 원문은 저장하지 않는다. 해시만 둔다
	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 20)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	// Asia/Seoul 달력월 한도 판정용. 한 번도 안 바꿨으면 null
	@Column
	private Instant nicknameChangedAt;

	protected Member() {
	}

	private Member(String email, String passwordHash, String nickname, MemberRole role, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
		this.role = role;
		this.createdAt = createdAt;
	}

	public static Member register(String email, String passwordHash, String nickname, Instant now) {
		return new Member(normalizeEmail(email), passwordHash, nickname.strip(), MemberRole.USER, now);
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
				throw new IllegalStateException("닉네임은 한 달에 한 번만 바꿀 수 있어요.");
			}
		}
		this.nickname = trimmed;
		this.nicknameChangedAt = now;
	}

	private static String requireNickname(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			throw new IllegalArgumentException("닉네임은 1~20자로 입력해 주세요.");
		}
		String trimmed = nickname.strip();
		if (trimmed.isEmpty() || trimmed.length() > 20) {
			throw new IllegalArgumentException("닉네임은 1~20자로 입력해 주세요.");
		}
		return trimmed;
	}

	// 같은 이메일이 대소문자·공백 차이로 두 번 가입되지 않게, 저장과 조회에 같은 형태를 쓴다
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getNickname() {
		return nickname;
	}

	public MemberRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getNicknameChangedAt() {
		return nicknameChangedAt;
	}

}
