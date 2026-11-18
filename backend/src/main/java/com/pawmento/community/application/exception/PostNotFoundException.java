package com.pawmento.community.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

// 없는 글이거나 지운 글
public class PostNotFoundException extends ResourceNotFoundException {
	public PostNotFoundException() {
		super("글을 찾을 수 없습니다.");
	}
}
