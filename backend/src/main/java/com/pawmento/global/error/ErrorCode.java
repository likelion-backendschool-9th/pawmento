package com.pawmento.global.error;

// 에러 응답의 code 값. 프론트엔드는 이 값으로 에러 종류를 구분한다
public enum ErrorCode {

	// 로그인이 필요하거나 로그인 정보가 맞지 않음 (401)
	UNAUTHORIZED,
	// 로그인은 했지만 권한이 없음 (403)
	FORBIDDEN,
	// 대상을 찾을 수 없음 (404)
	NOT_FOUND,
	// 이미 있는 값과 충돌 (409)
	CONFLICT,
	// 입력값이 규칙에 맞지 않음 (400)
	VALIDATION,
	// 파일 저장소를 사용할 수 없음 (503)
	STORAGE_UNAVAILABLE,
	// 저장된 데이터가 규칙에 맞지 않는 등 서버 쪽 문제 (500)
	INTERNAL_ERROR

}
