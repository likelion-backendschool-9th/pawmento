package com.pawmento.member.adapter.out.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.pawmento.global.security.AuthProperties;
import com.pawmento.global.security.JwtClaims;
import com.pawmento.member.application.port.out.IssueTokenPort;
import com.pawmento.member.domain.MemberRole;

@Component
class JwtTokenIssuer implements IssueTokenPort {

	private final JwtEncoder jwtEncoder;

	private final AuthProperties properties;

	JwtTokenIssuer(JwtEncoder jwtEncoder, AuthProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	@Override
	public String issue(Long memberId, MemberRole role) {
		Instant now = Instant.now();
		// 토큰은 누구나 내용을 읽을 수 있다. 이메일 같은 개인정보는 넣지 않는다
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(String.valueOf(memberId))
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenTtl()))
			.claim(JwtClaims.ROLE, role.name())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
