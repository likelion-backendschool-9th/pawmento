package com.pawmento.community.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.community.application.port.in.CreatePostCommand;
import com.pawmento.community.application.port.in.CreatePostUseCase;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.SavePostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

	private final LoadPostCategoryPort loadPostCategoryPort;
	private final SavePostPort savePostPort;

	@Override
	@Transactional
	public Long create(CreatePostCommand command) {
		PostCategory category = PostLookup.requireActiveCategory(loadPostCategoryPort, command.category());
		Post post = Post.create(command.memberId(), category, command.title(), command.body());
		return savePostPort.save(post);
	}

}
