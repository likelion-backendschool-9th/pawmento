package com.pawmento.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.application.exception.PostNotFoundException;
import com.pawmento.community.application.port.in.EditPostCommand;
import com.pawmento.community.application.port.in.PostResult;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.UpdatePostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;
import com.pawmento.community.domain.exception.NotPostAuthorException;

class EditPostServiceTest {

	private static final PostCategory CARE = PostCategory.restore(2L, "CARE", "건강·돌봄", 2, true);
	private static final PostCategory MEMORIAL = PostCategory.restore(4L, "MEMORIAL", "추모 방식", 4, true);
	private static final Instant CREATED = Instant.parse("2026-11-18T03:00:00Z");
	private static final Instant UPDATED = Instant.parse("2026-11-18T04:00:00Z");

	private final LoadPostPort posts = mock(LoadPostPort.class);
	private final LoadPostCategoryPort categories = mock(LoadPostCategoryPort.class);
	private final UpdatePostPort update = mock(UpdatePostPort.class);
	private final EditPostService service = new EditPostService(posts, categories, update);

	private Post stored() {
		return Post.restore(1L, 7L, CARE, "제목", "본문", CREATED, CREATED, false, 0L);
	}

	@Test
	@DisplayName("#21-S8 작성자가 고치면 저장하고 저장된 값(고친 시각)으로 돌려준다")
	void authorEdits() {
		when(posts.findById(1L)).thenReturn(Optional.of(stored()));
		when(categories.findByCode("MEMORIAL")).thenReturn(Optional.of(MEMORIAL));
		when(update.update(any())).thenAnswer(invocation -> {
			Post edited = invocation.getArgument(0);
			return Post.restore(edited.getId(), edited.getAuthorId(), edited.getCategory(), edited.getTitle(),
					edited.getBody(), edited.getCreatedAt(), UPDATED, false, 1L);
		});

		PostResult result = service.edit(new EditPostCommand(7L, 1L, "MEMORIAL", "고친 제목", "고친 본문"));

		assertThat(result.category()).isEqualTo("MEMORIAL");
		assertThat(result.categoryName()).isEqualTo("추모 방식");
		assertThat(result.title()).isEqualTo("고친 제목");
		assertThat(result.body()).isEqualTo("고친 본문");
		assertThat(result.updatedAt()).isEqualTo(UPDATED);
	}

	@Test
	@DisplayName("#21-S10 작성자가 아니면 도메인 예외가 그대로 나가고 저장하지 않는다")
	void otherMemberCannotEdit() {
		when(posts.findById(1L)).thenReturn(Optional.of(stored()));
		when(categories.findByCode("MEMORIAL")).thenReturn(Optional.of(MEMORIAL));

		assertThatThrownBy(() -> service.edit(new EditPostCommand(8L, 1L, "MEMORIAL", "고친 제목", "고친 본문")))
			.isInstanceOf(NotPostAuthorException.class);
		verify(update, never()).update(any());
	}

	@Test
	@DisplayName("#21-S10 남의 글이면 보낸 카테고리가 없거나 숨겨져 있어도 작성자 확인이 먼저다")
	void authorCheckComesBeforeCategory() {
		when(posts.findById(1L)).thenReturn(Optional.of(stored()));

		assertThatThrownBy(() -> service.edit(new EditPostCommand(8L, 1L, "NOPE", "고친 제목", "고친 본문")))
			.isInstanceOf(NotPostAuthorException.class);
		assertThatThrownBy(() -> service.edit(new EditPostCommand(8L, 1L, "HIDDEN", "고친 제목", "고친 본문")))
			.isInstanceOf(NotPostAuthorException.class);
		verify(categories, never()).findByCode(any());
		verify(update, never()).update(any());
	}

	@Test
	@DisplayName("#21-S9 없는 글이나 지운 글을 고치면 404 예외다")
	void missingPost() {
		when(posts.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.edit(new EditPostCommand(7L, 1L, "CARE", "제목", "본문")))
			.isInstanceOf(PostNotFoundException.class);
		verify(update, never()).update(any());
	}

}
