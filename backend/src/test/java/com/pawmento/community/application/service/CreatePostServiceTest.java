package com.pawmento.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pawmento.community.application.exception.InactivePostCategoryException;
import com.pawmento.community.application.exception.InvalidPostCategoryException;
import com.pawmento.community.application.port.in.CreatePostCommand;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.SavePostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;
import com.pawmento.community.domain.exception.InvalidPostException;

class CreatePostServiceTest {

	private static final PostCategory CARE = PostCategory.restore(2L, "CARE", "건강·돌봄", 2, true);
	private static final PostCategory HIDDEN = PostCategory.restore(9L, "HIDDEN", "숨김", 9, false);

	private final LoadPostCategoryPort categories = mock(LoadPostCategoryPort.class);
	private final SavePostPort save = mock(SavePostPort.class);
	private final CreatePostService service = new CreatePostService(categories, save);

	@Test
	@DisplayName("#21-S1 카테고리 코드로 카테고리를 찾아 글을 저장하고 id를 돌려준다")
	void createsPost() {
		when(categories.findByCode("CARE")).thenReturn(Optional.of(CARE));
		when(save.save(any())).thenReturn(1L);

		Long postId = service.create(new CreatePostCommand(7L, "CARE", "노견 관절 영양제 후기", "3개월 먹였어요"));

		assertThat(postId).isEqualTo(1L);
		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
		verify(save).save(saved.capture());
		assertThat(saved.getValue().getAuthorId()).isEqualTo(7L);
		assertThat(saved.getValue().getCategory()).isEqualTo(CARE);
		assertThat(saved.getValue().getTitle()).isEqualTo("노견 관절 영양제 후기");
	}

	@Test
	@DisplayName("#21-S2 제목이 규칙에 맞지 않으면 도메인 예외가 그대로 나가고 저장하지 않는다")
	void invalidTitleIsNotSaved() {
		when(categories.findByCode("CARE")).thenReturn(Optional.of(CARE));

		assertThatThrownBy(() -> service.create(new CreatePostCommand(7L, "CARE", "   ", "본문")))
			.isInstanceOf(InvalidPostException.class);
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#21-S3 없는 카테고리는 400 예외, 사용하지 않는 카테고리는 409 예외이고 저장하지 않는다")
	void invalidCategoryIsNotSaved() {
		when(categories.findByCode("NOPE")).thenReturn(Optional.empty());
		when(categories.findByCode("HIDDEN")).thenReturn(Optional.of(HIDDEN));

		assertThatThrownBy(() -> service.create(new CreatePostCommand(7L, "NOPE", "제목", "본문")))
			.isInstanceOf(InvalidPostCategoryException.class);
		assertThatThrownBy(() -> service.create(new CreatePostCommand(7L, null, "제목", "본문")))
			.isInstanceOf(InvalidPostCategoryException.class);
		assertThatThrownBy(() -> service.create(new CreatePostCommand(7L, "HIDDEN", "제목", "본문")))
			.isInstanceOf(InactivePostCategoryException.class);
		verify(save, never()).save(any());
	}

}
