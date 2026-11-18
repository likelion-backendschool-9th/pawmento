package com.pawmento.community.application.port.out;

import com.pawmento.community.domain.Post;

public interface SavePostPort {

	// 새 글을 저장하고 id를 돌려준다
	Long save(Post post);

}
