package com.pawmento.map.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

// 장소를 찾을 수 없다
public class PlaceNotFoundException extends ResourceNotFoundException {

	public PlaceNotFoundException() {
		super("장소를 찾을 수 없습니다.");
	}

}
