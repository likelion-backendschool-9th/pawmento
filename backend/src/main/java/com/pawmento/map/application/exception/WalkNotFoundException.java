package com.pawmento.map.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

// 산책을 찾을 수 없다
public class WalkNotFoundException extends ResourceNotFoundException {

	public WalkNotFoundException() {
		super("산책을 찾을 수 없습니다.");
	}

}
