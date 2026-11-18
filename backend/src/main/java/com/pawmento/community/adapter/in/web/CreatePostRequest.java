package com.pawmento.community.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

// 형식(필수, 길이)은 여기서 막고, 도메인(Post)도 같은 규칙을 마지막으로 한 번 더 검사한다
public record CreatePostRequest(
		@NotBlank(message = "카테고리를 선택해 주세요.")
		String category,

		@TextLength(max = 100, message = "제목은 1~100자로 입력해 주세요.")
		String title,

		@TextLength(max = 5000, message = "본문은 1~5000자로 입력해 주세요.")
		String body) {
}
