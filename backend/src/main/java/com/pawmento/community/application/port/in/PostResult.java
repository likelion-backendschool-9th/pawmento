package com.pawmento.community.application.port.in;

import java.time.Instant;

import com.pawmento.community.domain.Post;

public record PostResult(
		Long postId,
		String category,
		String categoryName,
		String title,
		String body,
		Long authorId,
		Instant createdAt,
		Instant updatedAt) {

	public static PostResult from(Post post) {
		return new PostResult(post.getId(), post.getCategory().getCode(), post.getCategory().getName(),
				post.getTitle(), post.getBody(), post.getAuthorId(), post.getCreatedAt(), post.getUpdatedAt());
	}

}
