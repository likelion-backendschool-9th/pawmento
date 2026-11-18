package com.pawmento.member.application.port.out;

import com.pawmento.member.domain.AuthProvider;

public record SocialProfile(
    AuthProvider provider,
    String providerUserId,
    String email,
    String nickname) {
}
