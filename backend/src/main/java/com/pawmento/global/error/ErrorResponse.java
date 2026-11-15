package com.pawmento.global.error;

/**
 * 에러 응답 본문. API 응답·에러 형식이 정해지기 전까지 쓰는 임시 형식이다 (docs/member/README.md).
 *
 * @param code 에러 종류
 * @param message 사용자에게 보여 줄 수 있는 설명
 */
public record ErrorResponse(ErrorCode code, String message) {
}
