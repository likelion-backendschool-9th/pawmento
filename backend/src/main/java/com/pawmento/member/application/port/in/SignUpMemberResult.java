package com.pawmento.member.application.port.in;

// 비밀번호 해시는 담지 않는다
public record SignUpMemberResult(Long id, String email, String nickname, String role) {
}
