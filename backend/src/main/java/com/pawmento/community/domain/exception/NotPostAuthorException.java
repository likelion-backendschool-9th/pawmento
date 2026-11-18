package com.pawmento.community.domain.exception;

import com.pawmento.global.exception.PermissionDeniedException;

// 작성자가 아닌 회원이 글을 고치거나 지우려 했다
public class NotPostAuthorException extends PermissionDeniedException {
	public NotPostAuthorException() {
		super("작성자만 글을 고치거나 지울 수 있습니다.");
	}
}
