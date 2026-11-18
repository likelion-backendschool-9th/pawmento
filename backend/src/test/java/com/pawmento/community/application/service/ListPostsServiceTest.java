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
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.application.exception.InactivePostCategoryException;
import com.pawmento.community.application.exception.InvalidPostCategoryException;
import com.pawmento.community.application.exception.InvalidPostPageException;
import com.pawmento.community.application.port.in.ListPostsQuery;
import com.pawmento.community.application.port.in.PostPageResult;
import com.pawmento.community.application.port.in.PostSummaryResult;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

class ListPostsServiceTest {

	private static final PostCategory LIFE = PostCategory.restore(1L, "LIFE", "반려 생활", 1, true);
	private static final PostCategory HIDDEN = PostCategory.restore(9L, "HIDDEN", "숨김", 9, false);
	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	private final LoadPostCategoryPort categories = mock(LoadPostCategoryPort.class);
	private final LoadPostPort posts = mock(LoadPostPort.class);
	private final ListPostsService service = new ListPostsService(categories, posts);

	// id가 from부터 1씩 줄어드는 글 count개 (최신순)
	private static List<Post> postsDescending(long from, int count) {
		return LongStream.iterate(from, id -> id - 1)
			.limit(count)
			.mapToObj(id -> Post.restore(id, 7L, LIFE, "글 " + id, "본문 " + id, NOW, NOW, false, 0L))
			.toList();
	}

	@Test
	@DisplayName("#21-S4 하나 더 읽어 다음 페이지가 있으면 마지막 글 id를 nextCursor로 준다")
	void firstPageHasNextCursor() {
		when(categories.findByCode("LIFE")).thenReturn(Optional.of(LIFE));
		when(posts.findPage(1L, null, 21)).thenReturn(postsDescending(25, 21));

		PostPageResult page = service.list(new ListPostsQuery("LIFE", 20, null));

		assertThat(page.posts()).hasSize(20);
		assertThat(page.posts().get(0).postId()).isEqualTo(25L);
		assertThat(page.nextCursor()).isEqualTo(6L);
	}

	@Test
	@DisplayName("#21-S4 마지막 페이지는 nextCursor가 null이다")
	void lastPageHasNoCursor() {
		when(categories.findByCode("LIFE")).thenReturn(Optional.of(LIFE));
		when(posts.findPage(1L, 6L, 21)).thenReturn(postsDescending(5, 5));

		PostPageResult page = service.list(new ListPostsQuery("LIFE", 20, 6L));

		assertThat(page.posts()).extracting(PostSummaryResult::postId).containsExactly(5L, 4L, 3L, 2L, 1L);
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	@DisplayName("#21-S4 size를 보내지 않으면 20개씩 준다")
	void defaultSizeIsTwenty() {
		when(categories.findByCode("LIFE")).thenReturn(Optional.of(LIFE));
		when(posts.findPage(1L, null, 21)).thenReturn(List.of());

		PostPageResult page = service.list(new ListPostsQuery("LIFE", null, null));

		assertThat(page.posts()).isEmpty();
		assertThat(page.nextCursor()).isNull();
		verify(posts).findPage(1L, null, 21);
	}

	@Test
	@DisplayName("#21-S4 목록은 본문 앞 100자만 준다")
	void previewIsFirstHundredCharacters() {
		String body = "가".repeat(99) + "🐶" + "나";
		Post post = Post.restore(3L, 7L, LIFE, "제목", body, NOW, NOW, false, 0L);
		when(categories.findByCode("LIFE")).thenReturn(Optional.of(LIFE));
		when(posts.findPage(1L, null, 21)).thenReturn(List.of(post));

		String preview = service.list(new ListPostsQuery("LIFE", null, null)).posts().get(0).preview();

		assertThat(preview).isEqualTo("가".repeat(99) + "🐶");
	}

	@Test
	@DisplayName("#21-S6 없는 카테고리는 400 예외, 사용하지 않는 카테고리는 409 예외다")
	void invalidCategory() {
		when(categories.findByCode("NOPE")).thenReturn(Optional.empty());
		when(categories.findByCode("HIDDEN")).thenReturn(Optional.of(HIDDEN));

		assertThatThrownBy(() -> service.list(new ListPostsQuery("NOPE", null, null)))
			.isInstanceOf(InvalidPostCategoryException.class);
		assertThatThrownBy(() -> service.list(new ListPostsQuery("HIDDEN", null, null)))
			.isInstanceOf(InactivePostCategoryException.class);
		verify(posts, never()).findPage(any(), any(), anyInt());
	}

	@Test
	@DisplayName("#21-S13 size가 1~50 밖이거나 cursor가 1 미만이면 보정하지 않고 거절한다")
	void outOfRangePageIsRejected() {
		when(categories.findByCode("LIFE")).thenReturn(Optional.of(LIFE));

		assertThatThrownBy(() -> service.list(new ListPostsQuery("LIFE", 0, null)))
			.isInstanceOf(InvalidPostPageException.class);
		assertThatThrownBy(() -> service.list(new ListPostsQuery("LIFE", 51, null)))
			.isInstanceOf(InvalidPostPageException.class);
		assertThatThrownBy(() -> service.list(new ListPostsQuery("LIFE", 20, 0L)))
			.isInstanceOf(InvalidPostPageException.class);
		verify(posts, never()).findPage(any(), any(), anyInt());
	}

}
