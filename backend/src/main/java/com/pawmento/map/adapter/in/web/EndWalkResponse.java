package com.pawmento.map.adapter.in.web;

import java.time.Instant;

import com.pawmento.map.application.port.in.EndWalkResult;

// 계획 문서의 종료 응답은 endedAt과 status다. 산책 id는 요청 경로에 이미 있어서 싣지 않는다
record EndWalkResponse(Instant endedAt, String status) {

	static EndWalkResponse from(EndWalkResult result) {
		return new EndWalkResponse(result.endedAt(), result.status());
	}

}
