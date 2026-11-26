package com.pawmento.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.application.exception.InactivePostCategoryException;
import com.pawmento.community.application.exception.InvalidPostCategoryException;
import com.pawmento.community.application.port.in.ListPostsQuery;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

// 20개씩 읽기와 nextCursor는 PostScenarioTest.listWithCursor가 확인한다.
// 여기에는 시나리오에 없는, 이모지가 섞인 본문의 100자 미리보기와 숨긴 카테고리의 409만 남긴다
class ListPostsServiceTest {

	private static final PostCategory LIFE = PostCategory.restore(1L, "LIFE", "반려 생활", 1, true);
	private static final PostCategory HIDDEN = PostCategory.restore(9L, "HIDDEN", "숨김", 9, false);
	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	private final LoadPostCategoryPort categories = mock(LoadPostCategoryPort.class);
	private final LoadPostPort posts = mock(LoadPostPort.class);
	private final ListPostsService service = new ListPostsService(categories, posts);

	// id가 from부터 1씩 줄어드는 글 count개 (최신순)
	@Test
	@DisplayName("#21-S4 목록은 본문 앞 100자만 준다")
	void previewIsFirstHundredCharacters() {
		String body = "가".repeat(99) + "🐶" + "나";
		Post post = Post.restore(3L, 7L, LIFE, "제목", body, NOW, NOW, false, 0L);
		when(categories.findByCode("LIFE")).thenReturn(Optional.of(LIFE));
		when(posts.findPage(1L, null, 21)).thenReturn(List.of(post));

		String preview = service.list(new ListPostsQuery("LIFE", 20, null)).posts().get(0).preview();

		assertThat(preview).isEqualTo("가".repeat(99) + "🐶");
	}

	@Test
	@DisplayName("#21-S6 없는 카테고리는 400 예외, 사용하지 않는 카테고리는 409 예외다")
	void invalidCategory() {
		when(categories.findByCode("NOPE")).thenReturn(Optional.empty());
		when(categories.findByCode("HIDDEN")).thenReturn(Optional.of(HIDDEN));

		assertThatThrownBy(() -> service.list(new ListPostsQuery("NOPE", 20, null)))
			.isInstanceOf(InvalidPostCategoryException.class);
		assertThatThrownBy(() -> service.list(new ListPostsQuery("HIDDEN", 20, null)))
			.isInstanceOf(InactivePostCategoryException.class);
		verify(posts, never()).findPage(any(), any(), anyInt());
	}

}
