package com.pawmento.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.member.application.exception.DuplicateMemberTagException;
import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.in.ChangeMemberNicknameUseCase;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.application.service.MemberTagGenerator;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.exception.MemberNicknameLimitException;

import jakarta.servlet.http.Cookie;

// 시나리오는 docs/member/account-tag.md에 있다.
// 태그 생성기만 "정해 둔 태그를 겹침 확인 없이 주는" 가짜로 바꿔, 실제 PostgreSQL의 유니크 제약 위반과 재시도를 일부러 만든다.
// 정해 둔 태그가 없으면 진짜 생성기처럼 동작한다. 테스트 메서드에는 트랜잭션을 두지 않는다(롤백을 확인해야 하므로)
@Import({ TestcontainersConfiguration.class, AccountTagScenarioTest.ScriptedTagConfig.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class AccountTagScenarioTest {

	private static final String PASSWORD = "correct-password";
	private static final String FOUR_DIGITS = "\\d{4}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ScriptedTags tags;

	@Autowired
	private SaveMemberPort saveMemberPort;

	@Autowired
	private LoadMemberPort loadMemberPort;

	@Autowired
	private ChangeMemberNicknameUseCase changeNickname;

	@TestConfiguration
	static class ScriptedTagConfig {

		@Bean
		@Primary
		ScriptedTags scriptedTags(ExistsMemberPort existsMemberPort) {
			return new ScriptedTags(existsMemberPort);
		}

	}

	@AfterEach
	void resetTags() {
		tags.reset();
	}

	@Test
	@DisplayName("#137-S1 #137-S3 이메일로 가입하면 숫자 4자리 태그를 받고 내 정보에도 같은 태그가 나온다")
	void signUpGetsTag() throws Exception {
		String email = uniqueEmail();

		String tag = signUp(email, "다윗");

		assertThat(tag).matches(FOUR_DIGITS);
		mockMvc.perform(get("/api/v1/members/me").cookie(login(email)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("다윗"))
			.andExpect(jsonPath("$.tag").value(tag));
	}

	@Test
	@DisplayName("#137-S5 #137-S4 같은 닉네임이면 태그와 한도가 그대로고, 닉네임을 바꾸면 이전과 다른 태그를 받는다")
	void nicknameChangeRedrawsTag() throws Exception {
		String email = uniqueEmail();
		String oldTag = signUp(email, "다윗");
		Cookie cookie = login(email);

		mockMvc.perform(patchNickname(cookie, "다윗"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tag").value(oldTag));

		// 같은 닉네임은 한도를 쓰지 않았으므로 이번 달에 바로 바꿀 수 있다
		String newNickname = uniqueNickname("보리");
		mockMvc.perform(patchNickname(cookie, newNickname))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(newNickname))
			.andExpect(jsonPath("$.tag").value(allOf(matchesPattern(FOUR_DIGITS), not(oldTag))));
	}

	@Test
	@DisplayName("#137-S10 가입 저장이 태그 제약에 걸리면 롤백하고 새 태그로 다시 저장한다")
	void signUpRetriesOnTagConflict() throws Exception {
		String nickname = uniqueNickname("보리");
		Member existing = saveMemberPort.save(Member.register(uniqueEmail(), "hash", nickname, "0427", Instant.now()));
		tags.script("0427", "0581");
		String email = uniqueEmail();

		String tag = signUp(email, nickname);

		assertThat(tag).isEqualTo("0581");
		assertThat(loadMemberPort.loadById(existing.getId()).orElseThrow().getTag()).isEqualTo("0427");
		assertThat(loadMemberPort.loadByEmail(email)).isPresent();
	}

	@Test
	@DisplayName("#137-S7 닉네임 변경 저장이 태그 제약에 걸리면 롤백하고 새 태그로 다시 저장한다")
	void nicknameChangeRetriesOnTagConflict() {
		String nickname = uniqueNickname("보리");
		Member a = saveMemberPort.save(Member.register(uniqueEmail(), "hash", nickname, "0427", Instant.now()));
		Member b = saveMemberPort.save(Member.register(uniqueEmail(), "hash", uniqueNickname("다윗"), "1111", Instant.now()));
		tags.script("0427", "0581");

		MemberResult result = changeNickname.changeNickname(new ChangeMemberNicknameCommand(b.getId(), nickname));

		assertThat(result.nickname()).isEqualTo(nickname);
		assertThat(result.tag()).isEqualTo("0581");
		assertThat(loadMemberPort.loadById(a.getId()).orElseThrow().getTag()).isEqualTo("0427");
	}

	@Test
	@DisplayName("#137-S7 두 회원이 동시에 같은 닉네임·태그로 바꾸면 진 쪽이 새 트랜잭션에서 다시 저장해 둘 다 성공한다")
	void concurrentChangesToSameNicknameBothSucceed() throws Exception {
		String nickname = uniqueNickname("보리");
		Member b = saveMemberPort.save(Member.register(uniqueEmail(), "hash", uniqueNickname("다윗"), "1111", Instant.now()));
		Member c = saveMemberPort.save(Member.register(uniqueEmail(), "hash", uniqueNickname("구름"), "2222", Instant.now()));
		// 처음 두 번의 태그 발급에서 두 요청을 만나게 해(둘 다 회원 행을 잠근 뒤) 같은 0427을 주고, 다시 시도하면 0581을 준다
		tags.script("0427", "0427", "0581");
		tags.meetFirst(2);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<MemberResult> first = executor.submit(() -> change(b, nickname));
			Future<MemberResult> second = executor.submit(() -> change(c, nickname));
			MemberResult one = first.get(10, TimeUnit.SECONDS);
			MemberResult two = second.get(10, TimeUnit.SECONDS);
			assertThat(List.of(one.tag(), two.tag())).containsExactlyInAnyOrder("0427", "0581");
			assertThat(List.of(one.nickname(), two.nickname())).containsOnly(nickname);
		}

		// 진 쪽의 첫 시도는 롤백됐으므로 두 회원 모두 한도를 한 번만 썼다
		assertThat(loadMemberPort.loadById(b.getId()).orElseThrow().getNicknameChangedAt()).isNotNull();
		assertThat(loadMemberPort.loadById(c.getId()).orElseThrow().getNicknameChangedAt()).isNotNull();
		assertThatThrownBy(() -> change(b, uniqueNickname("다시"))).isInstanceOf(MemberNicknameLimitException.class);
		assertThatThrownBy(() -> change(c, uniqueNickname("다시"))).isInstanceOf(MemberNicknameLimitException.class);
	}

	@Test
	@DisplayName("#137-S11 세 번 모두 태그 제약에 걸리면 409 예외이고 닉네임·태그·변경 시각은 요청 전과 같다")
	void givesUpAfterThreeConflictsAndKeepsMember() {
		String nickname = uniqueNickname("보리");
		saveMemberPort.save(Member.register(uniqueEmail(), "hash", nickname, "0427", Instant.now()));
		String original = uniqueNickname("다윗");
		Member b = saveMemberPort.save(Member.register(uniqueEmail(), "hash", original, "1111", Instant.now()));
		tags.script("0427", "0427", "0427");

		assertThatThrownBy(() -> change(b, nickname)).isInstanceOf(DuplicateMemberTagException.class);

		Member after = loadMemberPort.loadById(b.getId()).orElseThrow();
		assertThat(after.getNickname()).isEqualTo(original);
		assertThat(after.getTag()).isEqualTo("1111");
		assertThat(after.getNicknameChangedAt()).isNull();

		// 한도를 쓰지 않았으므로 이번 달에 다시 바꿀 수 있다
		tags.reset();
		assertThat(change(b, uniqueNickname("보리")).tag()).matches(FOUR_DIGITS);
	}

	private MemberResult change(Member member, String nickname) {
		return changeNickname.changeNickname(new ChangeMemberNicknameCommand(member.getId(), nickname));
	}

	private String signUp(String email, String nickname) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","nickname":"%s"}
						""".formatted(email, PASSWORD, nickname)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tag").value(matchesPattern(FOUR_DIGITS)))
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.tag");
	}

	private Cookie login(String email) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		Cookie cookie = result.getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Cookie(AccessTokenCookie.NAME, cookie.getValue());
	}

	private org.springframework.test.web.servlet.RequestBuilder patchNickname(Cookie cookie, String nickname) {
		return patch("/api/v1/members/me").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nickname":"%s"}
					""".formatted(nickname));
	}

	private String uniqueEmail() {
		return "tag-" + UUID.randomUUID() + "@example.com";
	}

	// 다른 테스트와 닉네임이 겹치지 않게 짧은 꼬리를 붙인다 (닉네임은 20자 이내)
	private String uniqueNickname(String base) {
		return base + UUID.randomUUID().toString().substring(0, 6);
	}

	// 정해 둔 태그를 겹침 확인 없이 차례로 준다. 다 쓰면 진짜 생성기처럼 동작한다.
	// meetFirst(n)이면 처음 n번의 호출이 서로 만날 때까지 기다린 뒤 태그를 받는다
	static class ScriptedTags extends MemberTagGenerator {

		private final Queue<String> script = new ConcurrentLinkedQueue<>();
		private final AtomicInteger waiting = new AtomicInteger();
		private volatile CyclicBarrier barrier;

		ScriptedTags(ExistsMemberPort existsMemberPort) {
			super(existsMemberPort);
		}

		void script(String... next) {
			script.clear();
			script.addAll(List.of(next));
		}

		void meetFirst(int calls) {
			waiting.set(calls);
			barrier = new CyclicBarrier(calls);
		}

		void reset() {
			script.clear();
			barrier = null;
			waiting.set(0);
		}

		@Override
		public String next(String nickname, String previousTag) {
			CyclicBarrier current = barrier;
			if (current != null && waiting.getAndDecrement() > 0) {
				try {
					current.await(5, TimeUnit.SECONDS);
				}
				catch (Exception e) {
					throw new IllegalStateException("두 요청이 만나지 못했습니다.", e);
				}
			}
			String scripted = script.poll();
			return scripted != null ? scripted : super.next(nickname, previousTag);
		}

	}

}
