package com.pawmento.community.application.port.in;

import com.pawmento.community.domain.PostCategory;

public record PostCategoryResult(String code, String name) {

	public static PostCategoryResult from(PostCategory category) {
		return new PostCategoryResult(category.getCode(), category.getName());
	}

}
