package com.pawmento.community.domain;

import java.time.Instant;

import com.pawmento.community.domain.exception.InvalidPostException;

import lombok.Getter;

// 커뮤니티 글. Spring·JPA를 모른다. 입력 규칙을 여기서 지킨다.
// 시각은 만들지 않는다. 영속성 어댑터가 채운 값을 불러올 때 받는다
@Getter
public class Post {

	public static final int TITLE_MAX_LENGTH = 100;
	public static final int BODY_MAX_LENGTH = 5000;

	private Long id;
	private final Long authorId;
	private final PostCategory category;
	private final String title;
	private final String body;
	private Instant createdAt;
	private Instant updatedAt;
	// 지우면 true가 되고, 영속성 어댑터가 deleted_at을 채운다
	private boolean deleted;
	// 낙관적 잠금용. DB에서 읽은 버전을 저장까지 그대로 나른다
	private Long version;

	private Post(Long authorId, PostCategory category, String title, String body) {
		this.authorId = authorId;
		this.category = category;
		this.title = title;
		this.body = body;
	}

	// 앞뒤 공백을 지우고 저장한다
	public static Post create(Long authorId, PostCategory category, String title, String body) {
		if (authorId == null) {
			throw new InvalidPostException("작성자가 필요합니다.");
		}
		return new Post(authorId, requireCategory(category), requireTitle(title), requireBody(body));
	}

	// DB에서 꺼낸 값을 도메인으로 되돌릴 때 쓴다. 이미 저장된 값이므로 생성 규칙을 다시 검사하지 않는다
	public static Post restore(Long id, Long authorId, PostCategory category, String title, String body,
			Instant createdAt, Instant updatedAt, boolean deleted, Long version) {
		Post post = new Post(authorId, category, title, body);
		post.id = id;
		post.createdAt = createdAt;
		post.updatedAt = updatedAt;
		post.deleted = deleted;
		post.version = version;
		return post;
	}

	private static PostCategory requireCategory(PostCategory category) {
		if (category == null) {
			throw new InvalidPostException("카테고리를 선택해 주세요.");
		}
		return category;
	}

	private static String requireTitle(String title) {
		return requireText(title, TITLE_MAX_LENGTH, "제목은 1~" + TITLE_MAX_LENGTH + "자로 입력해 주세요.");
	}

	private static String requireBody(String body) {
		return requireText(body, BODY_MAX_LENGTH, "본문은 1~" + BODY_MAX_LENGTH + "자로 입력해 주세요.");
	}

	private static String requireText(String value, int maxLength, String message) {
		if (value == null) {
			throw new InvalidPostException(message);
		}
		String stripped = strip(value);
		if (stripped.isEmpty() || stripped.length() > maxLength) {
			throw new InvalidPostException(message);
		}
		return stripped;
	}

	// 앞뒤 공백을 지운다. 기준은 프론트엔드의 trim()과 같다(isSpace)
	private static String strip(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && isSpace(value.charAt(start))) {
			start++;
		}
		while (end > start && isSpace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(start, end);
	}

	// JavaScript trim()이 지우는 문자: 탭·줄바꿈·U+000B·U+000C·U+000D, BOM(U+FEFF),
	// 공백 범주(Zs, U+00A0·U+3000 포함)와 줄·문단 구분자(U+2028·U+2029).
	// Character.isWhitespace는 U+001C~U+001F도 공백으로 보지만 trim()은 남기므로 쓰지 않는다
	private static boolean isSpace(char c) {
		return (c >= '\t' && c <= '\r') || c == '\uFEFF' || Character.isSpaceChar(c);
	}

}
