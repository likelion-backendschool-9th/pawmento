package com.pawmento.member.application.port.in;

public record ChangeMemberNicknameCommand(Long memberId, String nickname) {
}
