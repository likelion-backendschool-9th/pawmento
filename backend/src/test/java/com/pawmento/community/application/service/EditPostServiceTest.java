package com.pawmento.community.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.application.exception.PostNotFoundException;
import com.pawmento.community.application.port.in.EditPostCommand;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.UpdatePostPort;

// 작성자 수정, 남의 글 403, 작성자 확인이 카테고리 확인보다 먼저인 것은 PostScenarioTest가 확인한다.
// 여기에는 시나리오에 없는, 없는 글이나 지운 글을 고치는 경우만 남긴다
class EditPostServiceTest {

	private final LoadPostPort posts = mock(LoadPostPort.class);
	private final LoadPostCategoryPort categories = mock(LoadPostCategoryPort.class);
	private final UpdatePostPort update = mock(UpdatePostPort.class);
	private final EditPostService service = new EditPostService(posts, categories, update);

	@Test
	@DisplayName("#21-S9 없는 글이나 지운 글을 고치면 404 예외다")
	void missingPost() {
		when(posts.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.edit(new EditPostCommand(7L, 1L, "CARE", "제목", "본문")))
			.isInstanceOf(PostNotFoundException.class);
		verify(update, never()).update(any());
	}

}
