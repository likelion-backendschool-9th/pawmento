package com.pawmento.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.community.application.exception.PostConcurrentModificationException;
import com.pawmento.community.application.port.in.CreatePostCommand;
import com.pawmento.community.application.port.in.CreatePostUseCase;
import com.pawmento.community.application.port.in.DeletePostCommand;
import com.pawmento.community.application.port.in.DeletePostUseCase;
import com.pawmento.community.application.port.in.EditPostCommand;
import com.pawmento.community.application.port.in.EditPostUseCase;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.UpdatePostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.global.security.AccessTokenCookie;

import jakarta.servlet.http.Cookie;

// #21-S14 작성자의 요청 두 개가 겹치면 나중에 저장되는 요청이 덮어쓰지 않고 409 CONFLICT를 받는지 HTTP로 확인한다.
// 수정 요청(PUT)이 글을 읽은 직후에 끼어들 수 있게 LoadPostPort를 감싼다. 끼어든 요청은 다른 스레드에서 커밋까지 끝낸다
@Import({ TestcontainersConfiguration.class, PostConcurrentUpdateTest.HookConfig.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class PostConcurrentUpdateTest {

	private static final String PASSWORD = "correct-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HookedLoadPostPort hookedLoadPostPort;

	@Autowired
	private CreatePostUseCase createPostUseCase;

	@Autowired
	private EditPostUseCase editPostUseCase;

	@Autowired
	private DeletePostUseCase deletePostUseCase;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UpdatePostPort updatePostPort;

	@TestConfiguration
	static class HookConfig {

		@Bean
		@Primary
		HookedLoadPostPort hookedLoadPostPort(@Qualifier("postPersistenceAdapter") LoadPostPort delegate) {
			return new HookedLoadPostPort(delegate);
		}

	}

	// 다음 한 번의 글 읽기 직후에 정해 둔 작업을 끼워 넣는다
	static final class HookedLoadPostPort implements LoadPostPort {

		private final LoadPostPort delegate;
		private final AtomicReference<Runnable> afterNextLoad = new AtomicReference<>();

		HookedLoadPostPort(LoadPostPort delegate) {
			this.delegate = delegate;
		}

		void runAfterNextLoad(Runnable action) {
			afterNextLoad.set(action);
		}

		@Override
		public Optional<Post> findById(Long postId) {
			Optional<Post> found = delegate.findById(postId);
			Runnable action = afterNextLoad.getAndSet(null);
			if (action != null) {
				action.run();
			}
			return found;
		}

		@Override
		public List<Post> findPage(Long categoryId, Long cursor, int limit) {
			return delegate.findPage(categoryId, cursor, limit);
		}

	}

	@Test
	@DisplayName("#21-S14 수정이 글을 읽은 뒤 다른 요청이 글을 지우면 수정은 409 CONFLICT이고 지운 글은 되살아나지 않는다")
	void editAfterDeleteIsRejected() throws Exception {
		Member a = loginAsNewMember();
		Long postId = createPostUseCase.create(new CreatePostCommand(a.id(), "CARE", "원래 제목", "원래 본문"));
		hookedLoadPostPort
			.runAfterNextLoad(() -> inOtherThread(() -> deletePostUseCase.delete(new DeletePostCommand(a.id(), postId))));

		editPost(a.cookie(), postId, "늦은 수정", "늦은 본문").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT title, body, deleted_at FROM posts WHERE id = ?",
				postId);
		assertThat(row.get("deleted_at")).isNotNull();
		assertThat(row.get("title")).isEqualTo("원래 제목");
		assertThat(row.get("body")).isEqualTo("원래 본문");
	}

	@Test
	@DisplayName("#21-S14 두 수정이 겹치면 나중에 저장되는 쪽이 409 CONFLICT이고 먼저 저장된 값이 남는다")
	void laterEditIsRejected() throws Exception {
		Member a = loginAsNewMember();
		Long postId = createPostUseCase.create(new CreatePostCommand(a.id(), "CARE", "원래 제목", "원래 본문"));
		hookedLoadPostPort.runAfterNextLoad(() -> inOtherThread(() -> editPostUseCase
			.edit(new EditPostCommand(a.id(), postId, "CARE", "먼저 고친 제목", "먼저 고친 본문"))));

		editPost(a.cookie(), postId, "늦게 고친 제목", "늦게 고친 본문").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT title, body, deleted_at FROM posts WHERE id = ?",
				postId);
		assertThat(row.get("title")).isEqualTo("먼저 고친 제목");
		assertThat(row.get("body")).isEqualTo("먼저 고친 본문");
		assertThat(row.get("deleted_at")).isNull();
	}

	@Test
	@DisplayName("#21-S14 버전 충돌을 409 예외로 바꿀 때 저장소의 충돌 예외를 cause로 남긴다")
	void conflictKeepsCause() {
		Long postId = createPostUseCase.create(new CreatePostCommand(1L, "CARE", "원래 제목", "원래 본문"));
		Post stale = hookedLoadPostPort.findById(postId).orElseThrow();
		editPostUseCase.edit(new EditPostCommand(1L, postId, "CARE", "먼저 고친 제목", "먼저 고친 본문"));

		stale.edit(1L, stale.getCategory(), "늦게 고친 제목", "늦게 고친 본문");

		assertThatThrownBy(() -> updatePostPort.update(stale)).isInstanceOf(PostConcurrentModificationException.class)
			.hasCauseInstanceOf(OptimisticLockingFailureException.class);
	}

	// 다른 요청처럼 별도 스레드(별도 트랜잭션)에서 실행하고 커밋이 끝날 때까지 기다린다
	private static void inOtherThread(Runnable request) {
		AtomicReference<Throwable> error = new AtomicReference<>();
		Thread thread = new Thread(() -> {
			try {
				request.run();
			}
			catch (Throwable e) {
				error.set(e);
			}
		}, "other-request");
		thread.start();
		try {
			thread.join(10_000);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		assertThat(thread.isAlive()).as("끼어든 요청이 10초 안에 끝나야 한다").isFalse();
		assertThat(error.get()).as("끼어든 요청은 성공해야 한다").isNull();
	}

	private record Member(long id, Cookie cookie) {
	}

	private Member loginAsNewMember() throws Exception {
		String email = "community-concurrent-" + UUID.randomUUID() + "@example.com";
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
		Number id = JsonPath.read(login.getResponse().getContentAsString(), "$.id");
		return new Member(id.longValue(), new Cookie(AccessTokenCookie.NAME, cookie.getValue()));
	}

	private ResultActions editPost(Cookie cookie, Long postId, String title,
			String body) throws Exception {
		return mockMvc.perform(put("/api/v1/posts/" + postId).cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"category":"CARE","title":"%s","body":"%s"}
					""".formatted(title, body)));
	}

}
