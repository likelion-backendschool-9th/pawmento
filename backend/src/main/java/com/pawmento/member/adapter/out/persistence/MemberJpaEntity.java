package com.pawmento.member.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 기존 테이블·컬럼·제약 이름을 유지한다. DB 매핑은 도메인과 분리한다.
@Entity
@Table(name = "member", uniqueConstraints = {
		@UniqueConstraint(name = MemberJpaEntity.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"),
		@UniqueConstraint(name = MemberJpaEntity.NICKNAME_TAG_UNIQUE_CONSTRAINT,
			columnNames = { "nickname", "tag"}) })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MemberJpaEntity {
	static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";
	static final String NICKNAME_TAG_UNIQUE_CONSTRAINT = "uk_member_nickname_tag";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254)
	private String email;

	@Column
	private String passwordHash;

	@Column(nullable = false, length = 20)
	private String nickname;

	@Column(nullable = false, length = 4)
	private String tag;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column
	private Instant nicknameChangedAt;

	static MemberJpaEntity from(Member member) {
		MemberJpaEntity entity = new MemberJpaEntity();
		entity.id = member.getId();
		entity.email = member.getEmail();
		entity.passwordHash = member.getPasswordHash();
		entity.nickname = member.getNickname();
		entity.tag = member.getTag();
		entity.role = member.getRole();
		entity.createdAt = member.getCreatedAt();
		entity.nicknameChangedAt = member.getNicknameChangedAt();
		return entity;
	}

	Member toDomain() {
		return Member.restore(id, email, passwordHash, nickname, tag, role, createdAt, nicknameChangedAt);
	}
}