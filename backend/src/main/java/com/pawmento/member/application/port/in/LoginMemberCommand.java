package com.pawmento.member.application.port.in;

public record LoginMemberCommand(String email, String rawPassword) {
}
