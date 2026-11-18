package com.pawmento.community.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.community.application.port.in.EditPostCommand;
import com.pawmento.community.application.port.in.EditPostUseCase;
import com.pawmento.community.application.port.in.PostResult;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.UpdatePostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditPostService implements EditPostUseCase {

	private final LoadPostPort loadPostPort;
	private final LoadPostCategoryPort loadPostCategoryPort;
	private final UpdatePostPort updatePostPort;

	@Override
	@Transactional
	public PostResult edit(EditPostCommand command) {
		Post post = PostLookup.requirePost(loadPostPort, command.postId());
		// 남의 글이면 보낸 카테고리가 없거나 숨겨져 있어도 403이 먼저다
		post.requireWrittenBy(command.memberId());
		PostCategory category = PostLookup.requireActiveCategory(loadPostCategoryPort, command.category());
		post.edit(command.memberId(), category, command.title(), command.body());
		// 고친 시각은 저장할 때 정해지므로 저장된 글로 돌려준다
		return PostResult.from(updatePostPort.update(post));
	}

}
