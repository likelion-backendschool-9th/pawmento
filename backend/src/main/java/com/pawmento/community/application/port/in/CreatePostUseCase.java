package com.pawmento.community.application.port.in;

public interface CreatePostUseCase {

	// 만든 글의 id를 돌려준다
	Long create(CreatePostCommand command);

}
