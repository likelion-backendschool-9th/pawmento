package com.pawmento.member.domain;

import java.time.Instant;

import lombok.Getter;

@Getter 
public class SocialAccount {

    private Long id;

    private Long memberId;

    private AuthProvider provider;

    private String providerUserId;

    private Instant linkedAt;

    private SocialAccount(Long memberId, AuthProvider provider, String providerUserId, Instant linkedAt) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.linkedAt = linkedAt;
    }
    
    public static SocialAccount link(Long memberId, AuthProvider provider, String providerUserId, Instant now) {
        if (memberId == null) {
            throw new IllegalArgumentException("저장된 회원에만 소셜 계정을 연결할 수 있습니다.");
        }
        if (provider == null || provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("소셜 제공자가 필요합니다.");
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("제공자 사용자 id가 없습니다.");
        }
        return new SocialAccount(memberId, provider, providerUserId, now);
    }

    public static SocialAccount restore(Long id, Long memberId, AuthProvider provider, String providerUserId, Instant linkedAt) {
        SocialAccount account = new SocialAccount(memberId, provider, providerUserId, linkedAt);
        account.id = id;
        return account;
    }
}
