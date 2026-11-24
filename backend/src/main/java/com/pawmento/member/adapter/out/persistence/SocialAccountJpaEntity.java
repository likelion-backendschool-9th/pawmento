package com.pawmento.member.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.SocialAccount;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "social_account", uniqueConstraints = {
        @UniqueConstraint(name = SocialAccountJpaEntity.PROVIDER_USER_UNIQUE_CONSTRAINT,
            columnNames = { "provider", "provider_user_id" }),
        @UniqueConstraint(name = SocialAccountJpaEntity.MEMBER_PROVIDER_UNIQUE_CONSTRAINT,
            columnNames = { "member_id", "provider" }) })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SocialAccountJpaEntity {
    static final String PROVIDER_USER_UNIQUE_CONSTRAINT = "uk_social_account_provider_user";
    static final String MEMBER_PROVIDER_UNIQUE_CONSTRAINT = "uk_social_account_member_provider";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_social_account_member"))
    private MemberJpaEntity member;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false, updatable = false)
    private String providerUserId;

    @Column(nullable = false, updatable = false)
    private Instant linkedAt;

    static SocialAccountJpaEntity from(SocialAccount account) {
        SocialAccountJpaEntity entity = new SocialAccountJpaEntity();
        entity.id = account.getId();
        entity.memberId = account.getMemberId();
        entity.provider = account.getProvider();
        entity.providerUserId = account.getProviderUserId();
        entity.linkedAt = account.getLinkedAt();
        return entity;
    }

    SocialAccount toDomain() {
        return SocialAccount.restore(id, memberId, provider, providerUserId, linkedAt);
    }

}
