package com.pawmento.member.application.port.out;

import java.security.AuthProvider;

// 인가 코드로 소셜 제공자에게서 사용자 정보를 받아온다.
// 토큰 교환, HTTP 호출, 응답 형식은 구현(어댑터)이 맡는다
public interface LoadSocialProfilePort {
    
    SocialProfile load(AuthProvider provider, String code, String state, String redirectUri);
}
