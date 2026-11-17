package com.pawmento.member.application.port.in;

public record SignUpMemberCommand(String email, String rawPassword, String nickname) {
}
