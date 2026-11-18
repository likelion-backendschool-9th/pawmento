package com.pawmento.community.domain;

import lombok.Getter;

// 커뮤니티 카테고리. 행은 서버 시작 때 넣거나 DB에서 직접 바꾸므로 도메인에서 만들지 않고 불러오기만 한다
@Getter
public class PostCategory {

	private final Long id;
	// API와 주소에 쓰는 코드(CARE). 한 번 정하면 바꾸지 않는다
	private final String code;
	private final String name;
	private final int sortOrder;
	// false면 목록에 나오지 않고 새 글을 쓰거나 목록을 볼 수 없다
	private final boolean active;

	private PostCategory(Long id, String code, String name, int sortOrder, boolean active) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.sortOrder = sortOrder;
		this.active = active;
	}

	// DB에서 꺼낸 값을 도메인으로 되돌릴 때 쓴다
	public static PostCategory restore(Long id, String code, String name, int sortOrder, boolean active) {
		return new PostCategory(id, code, name, sortOrder, active);
	}

}
