package com.pawmento.member.application.dto;

// 로그인 결과. accessToken을 어떻게 전달할지(쿠키)는 웹 어댑터가 정한다
public record LoginResult(String accessToken, MemberResponse member) {
}
