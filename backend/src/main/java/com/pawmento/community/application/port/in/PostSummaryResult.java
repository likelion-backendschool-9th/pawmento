package com.pawmento.community.application.port.in;

import java.time.Instant;

import com.pawmento.community.domain.Post;

// 목록 한 줄. 본문은 앞부분(preview)만 준다
public record PostSummaryResult(
		Long postId,
		String category,
		String categoryName,
		String title,
		String preview,
		Long authorId,
		Instant createdAt) {

	public static final int PREVIEW_LENGTH = 100;

	public static PostSummaryResult from(Post post) {
		return new PostSummaryResult(post.getId(), post.getCategory().getCode(), post.getCategory().getName(),
				post.getTitle(), preview(post.getBody()), post.getAuthorId(), post.getCreatedAt());
	}

	// 이모지처럼 두 칸을 쓰는 글자가 반으로 잘리지 않게 코드 포인트 기준으로 자른다
	private static String preview(String body) {
		if (body.codePointCount(0, body.length()) <= PREVIEW_LENGTH) {
			return body;
		}
		return body.substring(0, body.offsetByCodePoints(0, PREVIEW_LENGTH));
	}

}
