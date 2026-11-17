package com.pawmento.member.application.port.in;

// accessToken을 어떻게 전달할지(쿠키)는 웹 어댑터가 정한다
public record LoginMemberResult(String accessToken, Long id, String email, String nickname, String role) {
}
