package com.pawmento.support;

import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.pawmento.member.application.port.in.SignUpMemberCommand;
import com.pawmento.member.application.service.VerificationCodes;

// 가입이 이메일 인증 토큰을 요구한 뒤(#279-S11), 가입을 준비 단계로만 쓰는 시나리오 테스트가 함께 쓰는 도우미.
// 메일 보내기·확인 API를 거치지 않고 "확인을 마친" 인증 줄을 DB에 바로 만들어 가입에 쓸 토큰을 준다.
// 가입 API는 이 토큰을 실제와 똑같이 검사하고 쓴다. 보내기·확인 자체는 member의 EmailVerification*ScenarioTest가 확인한다.
// 테스트 소스의 com.pawmento 아래라 @SpringBootTest마다 컴포넌트 스캔으로 들어온다
@Component
public class SignUpTokens {

	private final JdbcTemplate jdbcTemplate;

	private final VerificationCodes codes;

	public SignUpTokens(JdbcTemplate jdbcTemplate, VerificationCodes codes) {
		this.jdbcTemplate = jdbcTemplate;
		this.codes = codes;
	}

	// 시계를 과거로 고정한 테스트(Clock 빈 교체)도 있어 토큰 만료는 먼 미래로 둔다
	public String issue(String email) {
		String token = codes.newToken();
		jdbcTemplate.update("""
				insert into email_verification (email, purpose, code_hash, send_id, failed_attempts, code_expires_at,
					sent_at, verified_at, token_hash, token_expires_at)
				values (?, 'SIGNUP', ?, ?, 0, now() + interval '5 minutes', now(), now(), ?,
					timestamptz '9999-12-31 00:00:00+00')
				on conflict (email, purpose) do update set code_hash = excluded.code_hash, send_id = excluded.send_id,
					failed_attempts = 0, code_expires_at = excluded.code_expires_at, sent_at = excluded.sent_at,
					verified_at = excluded.verified_at, token_hash = excluded.token_hash,
					token_expires_at = excluded.token_expires_at
				""", email.strip().toLowerCase(Locale.ROOT), codes.hash("000000"), UUID.randomUUID().toString(),
				codes.hash(token));
		return token;
	}

	// 가입 서비스를 직접 부르는 테스트용. 같은 이메일의 토큰을 받아 가입 명령을 만든다
	public SignUpMemberCommand command(String email, String rawPassword, String nickname) {
		return new SignUpMemberCommand(email, rawPassword, nickname, issue(email));
	}

}
