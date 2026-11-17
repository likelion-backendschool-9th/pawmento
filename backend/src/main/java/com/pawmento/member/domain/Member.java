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

}
