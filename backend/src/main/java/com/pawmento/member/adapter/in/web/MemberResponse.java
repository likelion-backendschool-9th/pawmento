package com.pawmento.member.adapter.in.web;

// 회원 정보 응답. 비밀번호 해시는 담지 않는다
public record MemberResponse(Long id, String email, String nickname, String role) {
}
