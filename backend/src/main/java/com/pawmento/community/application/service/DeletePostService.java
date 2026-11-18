package com.pawmento.community.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.community.application.port.in.DeletePostCommand;
import com.pawmento.community.application.port.in.DeletePostUseCase;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.UpdatePostPort;
import com.pawmento.community.domain.Post;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeletePostService implements DeletePostUseCase {

	private final LoadPostPort loadPostPort;
	private final UpdatePostPort updatePostPort;

	// 행을 지우지 않고 지운 글로 표시한다(soft delete)
	@Override
	@Transactional
	public void delete(DeletePostCommand command) {
		Post post = PostLookup.requirePost(loadPostPort, command.postId());
		post.delete(command.memberId());
		updatePostPort.update(post);
	}

}
