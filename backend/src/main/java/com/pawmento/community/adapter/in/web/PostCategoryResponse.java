package com.pawmento.community.adapter.in.web;

import com.pawmento.community.application.port.in.PostCategoryResult;

public record PostCategoryResponse(String code, String name) {

	static PostCategoryResponse from(PostCategoryResult result) {
		return new PostCategoryResponse(result.code(), result.name());
	}

}
