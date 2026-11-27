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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.exception.VerificationMailUnavailableException;
import com.pawmento.member.application.port.out.SendVerificationMailPort;
import com.pawmento.member.domain.VerificationPurpose;
import com.pawmento.support.SignUpTokens;

// 시나리오는 docs/member/email-verification.md #279-S1~S4. 실제 메일 대신 보낸 메일을 기억하는 가짜 포트를 쓴다.
// 서비스는 트랜잭션 없이 문장마다 저장하므로 테스트에도 트랜잭션을 두지 않고, 끝나면 테스트 이메일을 지운다
@Import({ TestcontainersConfiguration.class, EmailVerificationSendScenarioTest.FakeMailConfig.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class EmailVerificationSendScenarioTest {

	private final String email = "send-" + UUID.randomUUID() + "@test.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SignUpTokens signUpTokens;

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

	record SentMail(String email, VerificationPurpose purpose, String code, Instant expiresAt) {
	}

	// 보낸 메일을 기억한다. failing이 켜져 있으면 메일 서버가 응답하지 않는 것처럼 실패한다
	static class FakeMailbox implements SendVerificationMailPort {

		private final List<SentMail> sent = new CopyOnWriteArrayList<>();

		private final AtomicBoolean failing = new AtomicBoolean();

		@Override
		public void send(String email, VerificationPurpose purpose, String code, Instant expiresAt) {
			if (failing.get()) {
				throw new VerificationMailUnavailableException(new IllegalStateException("메일 서버 응답 없음"));
			}
			sent.add(new SentMail(email, purpose, code, expiresAt));
		}

		List<SentMail> to(String email) {
			return sent.stream().filter(mail -> mail.email().equals(email)).toList();
		}

	}

	@BeforeEach
	void resetMailbox() {
		mailbox.failing.set(false);
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from email_verification where email = ?", email);
		jdbcTemplate.update("delete from member where email = ?", email);
	}

	private ResultActions send(String email, String purpose) throws Exception {
		String body = "{\"email\":\"" + email + "\",\"purpose\":\"" + purpose + "\"}";
		return mockMvc.perform(post("/api/v1/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private String storedHash() {
		return jdbcTemplate.queryForObject("select code_hash from email_verification where email = ?", String.class,
				email);
	}

	@Test
	@DisplayName("#279-S1 로그인 없이 보내면 202와 만료·다시 받기 시각을 받고, 6자리 번호는 메일로만 가며 DB에는 해시만 남는다")
	void sendsCode() throws Exception {
		send(email.toUpperCase(), "SIGNUP")
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.expiresAt").exists())
			.andExpect(jsonPath("$.retryAfter").exists())
			.andExpect(jsonPath("$.expiresInSeconds").value(allOf(greaterThanOrEqualTo(290), lessThanOrEqualTo(300))))
			.andExpect(jsonPath("$.retryAfterSeconds").value(allOf(greaterThanOrEqualTo(50), lessThanOrEqualTo(60))))
			.andExpect(jsonPath("$.code").doesNotExist());

		List<SentMail> mails = mailbox.to(email);
		assertThat(mails).hasSize(1);
		assertThat(mails.get(0).purpose()).isEqualTo(VerificationPurpose.SIGNUP);
		assertThat(mails.get(0).code()).matches("[0-9]{6}");
		assertThat(storedHash()).hasSize(64).isNotEqualTo(mails.get(0).code());
	}

	@Test
	@DisplayName("#279-S2 이미 가입된 이메일이면 409이고 메일이 가지 않는다")
	void registeredEmailIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"nickname\":\"보호자\",\"verificationToken\":\""
					+ signUpTokens.issue(email) + "\"}"))
			.andExpect(status().isCreated());

		send(email, "SIGNUP")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
		assertThat(mailbox.to(email)).isEmpty();
	}

	@Test
	@DisplayName("#279-S3 60초 안에 다시 요청하면 409이고, 60초가 지나면 새 번호가 가며 저장된 해시가 바뀐다")
	void resendAfterInterval() throws Exception {
		send(email, "SIGNUP").andExpect(status().isAccepted());
		String firstHash = storedHash();

		send(email, "SIGNUP")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("잠시 후 다시 요청해 주세요."));
		assertThat(mailbox.to(email)).hasSize(1);

		// 시계를 돌리는 대신 저장된 보낸 시각을 61초 앞으로 옮긴다
		jdbcTemplate.update("update email_verification set sent_at = sent_at - interval '61 seconds' where email = ?",
				email);
		send(email, "SIGNUP").andExpect(status().isAccepted());

		List<SentMail> mails = mailbox.to(email);
		assertThat(mails).hasSize(2);
		assertThat(mails.get(1).code()).isNotEqualTo(mails.get(0).code());
		assertThat(storedHash()).isNotEqualTo(firstHash);
	}

	@Test
	@DisplayName("#279-S4 메일 서버가 응답하지 않으면 503이고, 저장한 줄을 지워 바로 다시 요청할 수 있다")
	void mailFailureAllowsImmediateRetry() throws Exception {
		mailbox.failing.set(true);
		send(email, "SIGNUP")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from email_verification where email = ?",
				Integer.class, email)).isZero();

		mailbox.failing.set(false);
		send(email, "SIGNUP").andExpect(status().isAccepted());
		assertThat(mailbox.to(email)).hasSize(1);
	}

	@Test
	@DisplayName("#279-S1 이메일 형식이 틀리거나 이번에 열지 않은 용도면 400이고 메일이 가지 않는다")
	void invalidRequestIsRejected() throws Exception {
		send("not-an-email", "SIGNUP")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		send(email, "PASSWORD_RESET")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 인증 용도입니다."));
		send(email, "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		assertThat(mailbox.to(email)).isEmpty();
		assertThat(mailbox.to("not-an-email")).isEmpty();
	}
}
