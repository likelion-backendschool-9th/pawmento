package com.pawmento.community.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.pawmento.community.application.port.in.PostPageResult;
import com.pawmento.community.application.port.in.PostSummaryResult;

// nextCursor가 null이면 더 불러올 글이 없다
public record PostPageResponse(List<Item> posts, Long nextCursor) {

	public record Item(
			Long postId,
			String category,
			String categoryName,
			String title,
			String preview,
			Long authorId,
			Instant createdAt) {

		static Item from(PostSummaryResult result) {
			return new Item(result.postId(), result.category(), result.categoryName(), result.title(),
					result.preview(), result.authorId(), result.createdAt());
		}

	}

	static PostPageResponse from(PostPageResult result) {
		return new PostPageResponse(result.posts().stream().map(Item::from).toList(), result.nextCursor());
	}

}
