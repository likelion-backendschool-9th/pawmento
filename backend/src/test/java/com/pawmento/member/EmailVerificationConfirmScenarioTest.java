package com.pawmento.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.port.out.SendVerificationMailPort;
import com.pawmento.member.domain.VerificationPurpose;

// 시나리오는 docs/member/email-verification.md #279-S5~S7. 보낸 번호는 가짜 메일 포트에서 읽는다.
// 서비스는 트랜잭션 없이 문장마다 저장하므로 테스트에도 트랜잭션을 두지 않고, 끝나면 테스트 이메일을 지운다
@Import({ TestcontainersConfiguration.class, EmailVerificationConfirmScenarioTest.FakeMailConfig.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class EmailVerificationConfirmScenarioTest {

	private static final String INVALID = "인증번호가 맞지 않거나 만료됐어요.";

	private final String email = "confirm-" + UUID.randomUUID() + "@test.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FakeMailbox mailbox;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@TestConfiguration
	static class FakeMailConfig {

		@Bean
		@Primary
		FakeMailbox fakeMailbox() {
			return new FakeMailbox();
		}

	}

	// 보낸 번호를 이메일별로 기억한다
	static class FakeMailbox implements SendVerificationMailPort {

		private final List<String[]> sent = new CopyOnWriteArrayList<>();

		@Override
		public void send(String email, VerificationPurpose purpose, String code, Instant expiresAt) {
			sent.add(new String[] { email, code });
		}

		String lastCodeTo(String email) {
			return sent.stream().filter(mail -> mail[0].equals(email)).reduce((first, second) -> second)
					.map(mail -> mail[1]).orElseThrow();
		}

	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from email_verification where email = ?", email);
	}

	private String sendCode() throws Exception {
		mockMvc.perform(post("/api/v1/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"purpose\":\"SIGNUP\"}"))
			.andExpect(status().isAccepted());
		return mailbox.lastCodeTo(email);
	}

	private ResultActions confirm(String email, String code) throws Exception {
		String body = "{\"email\":\"" + email + "\",\"purpose\":\"SIGNUP\",\"code\":\"" + code + "\"}";
		return mockMvc.perform(post("/api/v1/auth/email-verifications/confirm").contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static String wrong(String code) {
		return code.equals("000000") ? "000001" : "000000";
	}

	private Integer failedAttempts() {
		return jdbcTemplate.queryForObject("select failed_attempts from email_verification where email = ?",
				Integer.class, email);
	}

	@Test
	@DisplayName("#279-S5 로그인 없이 맞는 번호를 넣으면 200과 가입 토큰을 받고, DB에는 토큰의 해시와 확인 시각만 남는다")
	void rightCodeReturnsToken() throws Exception {
		String code = sendCode();

		String body = confirm(email.toUpperCase(), code)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verificationToken").isString())
			.andExpect(jsonPath("$.expiresAt").exists())
			.andExpect(jsonPath("$.expiresInSeconds").value(allOf(greaterThanOrEqualTo(1790), lessThanOrEqualTo(1800))))
			.andReturn().getResponse().getContentAsString();

		String token = JsonPath.read(body, "$.verificationToken");
		assertThat(token).hasSize(43);
		String tokenHash = jdbcTemplate.queryForObject("select token_hash from email_verification where email = ?",
				String.class, email);
		assertThat(tokenHash).hasSize(64).isNotEqualTo(token);
		assertThat(jdbcTemplate.queryForObject("select verified_at is not null from email_verification where email = ?",
				Boolean.class, email)).isTrue();
	}

	@Test
	@DisplayName("#279-S5 틀린 번호, 보낸 적 없는 이메일은 같은 400 문구이고 틀린 번호만 한 번 센다")
	void wrongCodeAndUnknownEmailLookTheSame() throws Exception {
		String code = sendCode();

		confirm(email, wrong(code))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value(INVALID));
		assertThat(failedAttempts()).isEqualTo(1);

		confirm("nobody-" + UUID.randomUUID() + "@test.com", code)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(INVALID));
	}

	@Test
	@DisplayName("#279-S5 5분이 지난 번호는 맞아도 400이다")
	void expiredCodeIsRejected() throws Exception {
		String code = sendCode();
		jdbcTemplate.update("update email_verification set code_expires_at = now() - interval '1 second' where email = ?",
				email);

		confirm(email, code)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(INVALID));
	}

	@Test
	@DisplayName("#279-S6 다섯 번 틀린 뒤에는 맞는 번호도 400이고, 새로 받으면 다시 확인할 수 있다")
	void fiveFailuresBlockUntilResend() throws Exception {
		String code = sendCode();
		for (int i = 0; i < 5; i++) {
			confirm(email, wrong(code)).andExpect(status().isBadRequest());
		}
		assertThat(failedAttempts()).isEqualTo(5);

		confirm(email, code)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(INVALID));

		jdbcTemplate.update("update email_verification set sent_at = sent_at - interval '61 seconds' where email = ?",
				email);
		String newCode = sendCode();
		assertThat(failedAttempts()).isZero();
		confirm(email, newCode).andExpect(status().isOk());
	}

	@Test
	@DisplayName("#279-S7 한 번 확인한 번호로 다시 확인하면 400이다")
	void confirmOnlyOnce() throws Exception {
		String code = sendCode();
		confirm(email, code).andExpect(status().isOk());

		confirm(email, code)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(INVALID));
	}

	@Test
	@DisplayName("#279-S3 다시 받으면 이전 번호는 5분이 남았어도 400이고, 새 번호만 확인된다")
	void resendInvalidatesPreviousCode() throws Exception {
		String first = sendCode();
		jdbcTemplate.update("update email_verification set sent_at = sent_at - interval '61 seconds' where email = ?",
				email);
		String second = sendCode();

		confirm(email, first)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(INVALID));
		confirm(email, second).andExpect(status().isOk());
	}

	@Test
	@DisplayName("#279-S5 번호가 비었거나 이메일 형식이 틀리면 400 VALIDATION이고 틀린 횟수를 세지 않는다")
	void invalidRequestIsRejected() throws Exception {
		sendCode();

		confirm(email, "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		confirm("not-an-email", "123456")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		assertThat(failedAttempts()).isZero();
	}
}
