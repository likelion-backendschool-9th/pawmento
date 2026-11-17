package com.pawmento.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;

import jakarta.servlet.http.Cookie;

@Import({ TestcontainersConfiguration.class, NicknameScenarioTest.ClockConfig.class })
@SpringBootTest(properties = {
		"pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
		"spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class NicknameScenarioTest {

	private static final String PASSWORD = "correct-password";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Clock clock;

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		Clock clock() {
			return new MutableClock(Instant.parse("2026-11-17T03:00:00Z"), SEOUL);
		}

	}

	@Test
	@DisplayName("#52-S3 이번 달에 닉네임을 바꾸면 200이고 me에도 반영된다")
	void changeNicknameOncePerMonth() throws Exception {
		Cookie cookie = loginAsNewMember();

		mockMvc
			.perform(patch("/api/v1/members/me").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname":"콩이 보호자"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("콩이 보호자"));

		mockMvc.perform(get("/api/v1/members/me").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("콩이 보호자"));
	}

	@Test
	@DisplayName("#52-S4 같은 달에 다시 바꾸면 409 CONFLICT이고 닉네임은 그대로다")
	void changeNicknameTwiceInSameMonth() throws Exception {
		Cookie cookie = loginAsNewMember();
		mockMvc
			.perform(patch("/api/v1/members/me").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname":"첫번째"}
						"""))
			.andExpect(status().isOk());

		mockMvc
			.perform(patch("/api/v1/members/me").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname":"두번째"}
						"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));

		mockMvc.perform(get("/api/v1/members/me").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("첫번째"));
	}

	@Test
	@DisplayName("#52-S5 달력월이 바뀌면 다시 닉네임을 바꿀 수 있다")
	void changeNicknameAcrossMonthBoundary() throws Exception {
		((MutableClock) clock).setInstant(Instant.parse("2026-11-30T14:59:00Z")); // 2026-11-30 23:59 KST
		Cookie cookie = loginAsNewMember();
		mockMvc
			.perform(patch("/api/v1/members/me").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname":"11월 보호자"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("11월 보호자"));

		((MutableClock) clock).setInstant(Instant.parse("2026-11-30T15:00:00Z")); // 2026-12-01 00:00 KST
		mockMvc
			.perform(patch("/api/v1/members/me").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname":"12월 보호자"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("12월 보호자"));

		mockMvc.perform(get("/api/v1/members/me").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("12월 보호자"));
	}

	private Cookie loginAsNewMember() throws Exception {
		String email = "guardian-" + UUID.randomUUID() + "@example.com";
		mockMvc
			.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","nickname":"보호자"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated());
		MvcResult login = mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		Cookie cookie = login.getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Cookie(AccessTokenCookie.NAME, cookie.getValue());
	}

}
