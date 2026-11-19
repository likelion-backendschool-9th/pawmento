package com.pawmento.member.application.port.in;

public record SocialLoginMemberCommand(
    String provider,
    String code,
    String state,
    String redirectUri
) {
    
}
