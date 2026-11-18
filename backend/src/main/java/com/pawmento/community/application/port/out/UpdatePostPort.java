package com.pawmento.community.application.port.out;

import com.pawmento.community.domain.Post;

public interface UpdatePostPort {

	// 고치거나 지운 글을 저장하고, 저장된 값(고친 시각, 올라간 버전)을 담은 글을 돌려준다.
	// 읽은 뒤 다른 요청이 먼저 저장했으면 PostConcurrentModificationException을 던진다
	Post update(Post post);

}
