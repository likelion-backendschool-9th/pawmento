package com.pawmento.map.application.exception;

// 지도 영역이 규칙에 맞지 않을 때 (범위를 벗어났거나 최솟값이 최댓값보다 큼)
public class InvalidMapAreaException extends RuntimeException {

	public InvalidMapAreaException(String message, Throwable cause) {
		super(message, cause);
	}

}
