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
import org.mockito.ArgumentCaptor;

import com.pawmento.community.application.exception.PostNotFoundException;
import com.pawmento.community.application.port.in.DeletePostCommand;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.UpdatePostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;
import com.pawmento.community.domain.exception.NotPostAuthorException;

class DeletePostServiceTest {

	private static final PostCategory CARE = PostCategory.restore(2L, "CARE", "건강·돌봄", 2, true);
	private static final Instant CREATED = Instant.parse("2026-11-18T03:00:00Z");

	private final LoadPostPort posts = mock(LoadPostPort.class);
	private final UpdatePostPort update = mock(UpdatePostPort.class);
	private final DeletePostService service = new DeletePostService(posts, update);

	private Post stored() {
		return Post.restore(1L, 7L, CARE, "제목", "본문", CREATED, CREATED, false, 0L);
	}

	@Test
	@DisplayName("#21-S9 작성자가 지우면 지운 글로 표시해 저장한다")
	void authorDeletes() {
		when(posts.findById(1L)).thenReturn(Optional.of(stored()));

		service.delete(new DeletePostCommand(7L, 1L));

		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
		verify(update).update(saved.capture());
		assertThat(saved.getValue().isDeleted()).isTrue();
	}

	@Test
	@DisplayName("#21-S10 작성자가 아니면 지우지 않는다")
	void otherMemberCannotDelete() {
		when(posts.findById(1L)).thenReturn(Optional.of(stored()));

		assertThatThrownBy(() -> service.delete(new DeletePostCommand(8L, 1L)))
			.isInstanceOf(NotPostAuthorException.class);
		verify(update, never()).update(any());
	}

	@Test
	@DisplayName("#21-S9 지운 글을 다시 지우면 404 예외다")
	void deletedPostIsNotFound() {
		when(posts.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(new DeletePostCommand(7L, 1L)))
			.isInstanceOf(PostNotFoundException.class);
	}

}
