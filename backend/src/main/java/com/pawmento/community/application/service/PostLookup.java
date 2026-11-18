package com.pawmento.community.application.service;

import com.pawmento.community.application.exception.InactivePostCategoryException;
import com.pawmento.community.application.exception.InvalidPostCategoryException;
import com.pawmento.community.application.exception.PostNotFoundException;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

// 여러 서비스가 같은 방식으로 글과 카테고리를 찾는다. 저장소를 봐야 알 수 있는 규칙이라 서비스에 둔다
final class PostLookup {

	private PostLookup() {
	}

	// 없는 글과 지운 글은 똑같이 404다
	static Post requirePost(LoadPostPort loadPostPort, Long postId) {
		return loadPostPort.findById(postId).orElseThrow(PostNotFoundException::new);
	}

	// 없는 코드는 400, 사용하지 않는 카테고리는 409다
	static PostCategory requireActiveCategory(LoadPostCategoryPort loadPostCategoryPort, String code) {
		if (code == null || code.isBlank()) {
			throw new InvalidPostCategoryException();
		}
		PostCategory category = loadPostCategoryPort.findByCode(code).orElseThrow(InvalidPostCategoryException::new);
		if (!category.isActive()) {
			throw new InactivePostCategoryException();
		}
		return category;
	}

}
