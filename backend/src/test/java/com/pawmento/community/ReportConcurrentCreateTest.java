package com.pawmento.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.community.application.exception.DuplicateReportException;
import com.pawmento.community.application.port.in.CreatePostCommand;
import com.pawmento.community.application.port.in.CreatePostUseCase;
import com.pawmento.community.application.port.in.DeletePostCommand;
import com.pawmento.community.application.port.in.DeletePostUseCase;
import com.pawmento.community.application.port.in.EditPostCommand;
import com.pawmento.community.application.port.in.EditPostUseCase;
import com.pawmento.community.application.port.in.report.ReportPostCommand;
import com.pawmento.community.application.port.in.report.ReportPostUseCase;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.report.LoadLatestReportPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.report.PreviousReport;

// 접수가 확인을 마친 직후에 다른 요청을 끼워 넣어 순서를 정한다. 끼어든 요청은 다른 스레드(별도 트랜잭션)에서 커밋까지 끝낸다.
// 실제 PostgreSQL에서 부분 고유 인덱스와 잠그지 않는 스냅샷 읽기를 확인한다. HTTP 변환(409 본문)은 ReportScenarioTest가 본다
@Import({ TestcontainersConfiguration.class, ReportConcurrentCreateTest.HookConfig.class })
@SpringBootTest
class ReportConcurrentCreateTest {

	private static final Long AUTHOR = 2L;
	private static final Long REPORTER = 1L;

	@Autowired
	private Hooks hooks;

	@Autowired
	private ReportPostUseCase reportPostUseCase;

	@Autowired
	private CreatePostUseCase createPostUseCase;

	@Autowired
	private EditPostUseCase editPostUseCase;

	@Autowired
	private DeletePostUseCase deletePostUseCase;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("#112-S5 같은 회원의 신고 두 개가 모두 중복 확인을 통과하면 하나만 접수되고 나머지는 409 예외다")
	void concurrentDuplicateReport() {
		Long postId = createPost();
		AtomicReference<Long> otherId = new AtomicReference<>();
		hooks.afterNextLatestLoad(() -> inOtherThread(() -> otherId.set(report(postId))));

		assertThatThrownBy(() -> report(postId)).isInstanceOf(DuplicateReportException.class)
			.hasMessage("이미 신고한 게시글이에요.")
			.hasCauseInstanceOf(DataIntegrityViolationException.class);

		assertThat(otherId.get()).isNotNull();
		assertThat(jdbcTemplate.queryForList(
				"SELECT id FROM post_reports WHERE post_id = ? AND reporter_id = ? AND status = 'PENDING'", Long.class,
				postId, REPORTER))
			.containsExactly(otherId.get());
	}

	@Test
	@DisplayName("#112-S13 글을 읽은 뒤 작성자의 수정이 먼저 커밋돼도 접수되고 처음 읽은 내용이 스냅샷으로 남는다")
	void editCommittedAfterRead() {
		Long postId = createPost();
		Long version = postVersion(postId);
		hooks.afterNextPostLoad(() -> inOtherThread(
				() -> editPostUseCase.edit(new EditPostCommand(AUTHOR, postId, "CARE", "수정한 글", "고친 본문"))));

		Long reportId = report(postId);

		assertThat(postVersion(postId)).isGreaterThan(version);
		assertOriginalSnapshot(reportId, version);
	}

	@Test
	@DisplayName("#112-S13 글을 읽은 뒤 작성자의 삭제가 먼저 커밋돼도 접수되고 처음 읽은 내용이 스냅샷으로 남는다")
	void deleteCommittedAfterRead() {
		Long postId = createPost();
		Long version = postVersion(postId);
		hooks.afterNextPostLoad(
				() -> inOtherThread(() -> deletePostUseCase.delete(new DeletePostCommand(AUTHOR, postId))));

		Long reportId = report(postId);

		assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM posts WHERE id = ?", Object.class, postId))
			.isNotNull();
		assertOriginalSnapshot(reportId, version);
	}

	private void assertOriginalSnapshot(Long reportId, Long version) {
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM post_reports WHERE id = ?", reportId);
		assertThat(row).containsEntry("status", "PENDING")
			.containsEntry("snapshot_title", "산책 후기")
			.containsEntry("snapshot_body", "공원 한 바퀴")
			.containsEntry("snapshot_category_code", "LIFE")
			.containsEntry("snapshot_post_version", version);
	}

	private Long createPost() {
		return createPostUseCase.create(new CreatePostCommand(AUTHOR, "LIFE", "산책 후기", "공원 한 바퀴"));
	}

	private Long report(Long postId) {
		return reportPostUseCase.report(new ReportPostCommand(REPORTER, postId, "SPAM", null));
	}

	private Long postVersion(Long postId) {
		return jdbcTemplate.queryForObject("SELECT version FROM posts WHERE id = ?", Long.class, postId);
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

	@TestConfiguration
	static class HookConfig {

		@Bean
		Hooks hooks() {
			return new Hooks();
		}

		@Bean
		@Primary
		LoadPostPort hookedLoadPostPort(@Qualifier("postPersistenceAdapter") LoadPostPort delegate, Hooks hooks) {
			return new LoadPostPort() {
				@Override
				public Optional<Post> findById(Long postId) {
					Optional<Post> found = delegate.findById(postId);
					hooks.runAfterPostLoad();
					return found;
				}

				@Override
				public List<Post> findPage(Long categoryId, Long cursor, int limit) {
					return delegate.findPage(categoryId, cursor, limit);
				}
			};
		}

		@Bean
		@Primary
		LoadLatestReportPort hookedLoadLatestReportPort(
				@Qualifier("reportPersistenceAdapter") LoadLatestReportPort delegate, Hooks hooks) {
			return (postId, reporterId) -> {
				Optional<PreviousReport> found = delegate.findLatest(postId, reporterId);
				hooks.runAfterLatestLoad();
				return found;
			};
		}

	}

	// 다음 한 번의 읽기 직후에 정해 둔 작업을 끼워 넣는다. 끼어든 요청 안의 읽기에는 다시 끼어들지 않는다
	static final class Hooks {

		private final AtomicReference<Runnable> afterPostLoad = new AtomicReference<>();
		private final AtomicReference<Runnable> afterLatestLoad = new AtomicReference<>();

		void afterNextPostLoad(Runnable action) {
			afterPostLoad.set(action);
		}

		void afterNextLatestLoad(Runnable action) {
			afterLatestLoad.set(action);
		}

		void runAfterPostLoad() {
			run(afterPostLoad);
		}

		void runAfterLatestLoad() {
			run(afterLatestLoad);
		}

		private static void run(AtomicReference<Runnable> slot) {
			Runnable action = slot.getAndSet(null);
			if (action != null) {
				action.run();
			}
		}

	}

}
