package com.pawmento.community.application.port.out;

import java.util.List;
import java.util.Optional;

import com.pawmento.community.domain.Post;

// 지운 글은 돌려주지 않는다
public interface LoadPostPort {

	Optional<Post> findById(Long postId);

	// 카테고리의 글을 최신순(id 내림차순)으로 limit개까지 돌려준다. cursor가 있으면 id < cursor인 글만 고른다
	List<Post> findPage(Long categoryId, Long cursor, int limit);

}
