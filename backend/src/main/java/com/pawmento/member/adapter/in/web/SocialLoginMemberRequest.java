package com.pawmento.member.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

// 제공자가 돌려준 인가 코드로 로그인한다. state는 네이버처럼 요구하는 제공자에서만 쓴다
public record SocialLoginMemberRequest(
        @NotBlank(message = "인가 코드가 없습니다.")
        String code,

        String state,

        @NotBlank(message = "redirectUri가 없습니다.")
        String redirectUri) {

    // 인가 코드는 짧은 시간 동안 토큰으로 바꿀 수 있는 값이라 로그에 남기지 않는다
    @Override
    public String toString() {
        return "SocialLoginMemberRequest[code=****, state=" + state + ", redirectUri=" + redirectUri + "]";
    }
}
