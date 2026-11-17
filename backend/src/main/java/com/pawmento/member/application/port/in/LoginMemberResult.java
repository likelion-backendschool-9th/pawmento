package com.pawmento.member.application.port.in;

// 쿠키·HTTP를 모르는 로그인 결과다.
public record LoginMemberResult(String accessToken, MemberResult member) {
}
