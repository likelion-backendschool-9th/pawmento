package com.pawmento.member.domain;

import java.util.Locale;
import java.util.Optional;

public enum AuthProvider {
    
    LOCAL,
    GOOGLE,
    KAKAO,
    NAVER;

    // ★ "google" 같은 이름을 소셜 제공자로 바꾼다. LOCAL이거나 없는 이름이면 빈 값
    public static Optional<AuthProvider> findSocial(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AuthProvider provider : values()) {
            if (provider != LOCAL && provider.name().equals(name.toUpperCase(Locale.ROOT))) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
