package com.pawmento.community.adapter.in.web;

import java.time.Instant;

import com.pawmento.community.application.port.in.PostResult;

public record PostResponse(
		Long postId,
		String category,
		String categoryName,
		String title,
		String body,
		Long authorId,
		Instant createdAt,
		Instant updatedAt) {

	static PostResponse from(PostResult result) {
		return new PostResponse(result.postId(), result.category(), result.categoryName(), result.title(),
				result.body(), result.authorId(), result.createdAt(), result.updatedAt());
	}

}
