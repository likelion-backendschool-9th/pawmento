package com.pawmento.community.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.community.application.port.in.GetPostQuery;
import com.pawmento.community.application.port.in.GetPostUseCase;
import com.pawmento.community.application.port.in.PostResult;
import com.pawmento.community.application.port.out.LoadPostPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetPostService implements GetPostUseCase {

	private final LoadPostPort loadPostPort;

	// 사용하지 않는 카테고리의 글도 상세는 그대로 보여 준다
	@Override
	@Transactional(readOnly = true)
	public PostResult get(GetPostQuery query) {
		return PostResult.from(PostLookup.requirePost(loadPostPort, query.postId()));
	}

}
