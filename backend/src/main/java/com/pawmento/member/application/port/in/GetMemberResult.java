package com.pawmento.member.application.port.in;

public record GetMemberResult(Long id, String email, String nickname, String role) {
}
