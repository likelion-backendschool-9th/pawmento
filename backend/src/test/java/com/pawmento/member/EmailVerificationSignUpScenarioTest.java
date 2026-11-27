package com.pawmento.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.port.out.SendVerificationMailPort;
import com.pawmento.member.application.service.MemberTagGenerator;
import com.pawmento.member.domain.VerificationPurpose;

// 시나리오는 docs/member/email-verification.md #279-S8~S10. 보내기 → 확인 → 가입을 실제 API로 잇는다.
// 가입은 트랜잭션 안에서 토큰을 쓰고, 테스트 메서드에는 트랜잭션을 두지 않아 커밋된 결과를 본다
@Import({ TestcontainersConfiguration.class, EmailVerificationSignUpScenarioTest.FakeMailConfig.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class EmailVerificationSignUpScenarioTest {

	private static final String PASSWORD = "password123";
	private static final String REQUIRED = "이메일 인증을 다시 해 주세요.";

	private final List<String> emails = new CopyOnWriteArrayList<>();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FakeMailbox mailbox;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private MemberTagGenerator tags;

	@TestConfiguration
	static class FakeMailConfig {

		@Bean
		@Primary
		FakeMailbox fakeMailbox() {
			return new FakeMailbox();
		}

	}

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
		for (String email : emails) {
			jdbcTemplate.update("delete from email_verification where email = ?", email);
			jdbcTemplate.update("delete from member where email = ?", email);
		}
	}

	private String newEmail() {
		String email = "signup-" + UUID.randomUUID() + "@test.com";
		emails.add(email);
		return email;
	}

	// 실제 API로 보내기와 확인을 마치고 가입 토큰을 받는다
	private String verify(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"purpose\":\"SIGNUP\"}"))
			.andExpect(status().isAccepted());
		String body = mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"purpose\":\"SIGNUP\",\"code\":\"" + mailbox.lastCodeTo(email)
						+ "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.verificationToken");
	}

	private ResultActions signUp(String email, String nickname, String token) throws Exception {
		String tokenField = token == null ? "" : ",\"verificationToken\":\"" + token + "\"";
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"" + nickname + "\""
						+ tokenField + "}"));
	}

	private int members(String email) {
		return jdbcTemplate.queryForObject("select count(*) from member where email = ?", Integer.class, email);
	}

	private int verifications(String email) {
		return jdbcTemplate.queryForObject("select count(*) from email_verification where email = ?", Integer.class,
				email);
	}

	@Test
	@DisplayName("#279-S8 인증을 마친 이메일은 받은 토큰으로 가입하고, 쓴 인증 줄은 지워진다")
	void signUpWithToken() throws Exception {
		String email = newEmail();
		String token = verify(email);

		signUp(email.toUpperCase(), "보호자", token)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email));

		assertThat(members(email)).isEqualTo(1);
		assertThat(verifications(email)).isZero();
	}

	@Test
	@DisplayName("#279-S8 한 번 쓴 토큰으로는 다른 이메일로도 다시 가입할 수 없다")
	void tokenIsSingleUse() throws Exception {
		String email = newEmail();
		String token = verify(email);
		signUp(email, "보호자", token).andExpect(status().isCreated());

		String other = newEmail();
		signUp(other, "보호자", token)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(REQUIRED));
		assertThat(members(other)).isZero();
	}

	@Test
	@DisplayName("#279-S9 토큰이 없거나, 틀렸거나, 다른 이메일의 것이거나, 만료됐으면 400이고 회원이 생기지 않는다")
	void signUpRequiresValidToken() throws Exception {
		String email = newEmail();
		String other = newEmail();
		String otherToken = verify(other);

		signUp(email, "보호자", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value(REQUIRED));
		signUp(email, "보호자", "not-a-real-token")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(REQUIRED));
		signUp(email, "보호자", otherToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(REQUIRED));
		assertThat(members(email)).isZero();
		// 다른 이메일에 잘못 쓰인 토큰은 지워지지 않아 원래 이메일은 그대로 가입할 수 있다
		assertThat(verifications(other)).isEqualTo(1);

		String token = verify(email);
		jdbcTemplate.update("update email_verification set token_expires_at = now() - interval '1 second' where email = ?",
				email);
		signUp(email, "보호자", token)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(REQUIRED));
		assertThat(members(email)).isZero();
	}

	@Test
	@DisplayName("#279-S9 번호만 받고 확인하지 않은 이메일은 토큰이 없어 가입할 수 없다")
	void unconfirmedEmailCannotSignUp() throws Exception {
		String email = newEmail();
		mockMvc.perform(post("/api/v1/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"purpose\":\"SIGNUP\"}"))
			.andExpect(status().isAccepted());

		signUp(email, "보호자", "guessed-token").andExpect(status().isBadRequest());
		assertThat(members(email)).isZero();
		assertThat(verifications(email)).isEqualTo(1);
	}

	@Test
	@DisplayName("#279-S10 같은 토큰으로 가입이 동시에 와도 한 번만 성공하고 회원은 한 명이다")
	void concurrentSignUpsWithSameToken() throws Exception {
		String email = newEmail();
		String token = verify(email);
		int requests = 4;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return signUp(email, "보호자", token).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();

			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
			assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 400 || code == 409);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(members(email)).isEqualTo(1);
	}

	@Test
	@DisplayName("#279-S10 태그 충돌로 첫 시도가 롤백되면 쓴 토큰도 되살아나 다시 시도가 같은 토큰으로 성공한다")
	void tagCollisionRestoresToken() throws Exception {
		String first = newEmail();
		signUp(first, "콩이네", verify(first)).andExpect(status().isCreated());
		String takenTag = jdbcTemplate.queryForObject("select tag from member where email = ?", String.class, first);

		String email = newEmail();
		String token = verify(email);
		// 첫 시도에는 이미 쓰인 태그를 주고, 다시 시도부터는 원래대로 만든다
		doReturn(takenTag).doCallRealMethod().when(tags).next(eq("콩이네"));

		signUp(email, "콩이네", token).andExpect(status().isCreated());

		assertThat(members(email)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select tag from member where email = ?", String.class, email))
				.isNotEqualTo(takenTag);
		assertThat(verifications(email)).isZero();
	}
}
