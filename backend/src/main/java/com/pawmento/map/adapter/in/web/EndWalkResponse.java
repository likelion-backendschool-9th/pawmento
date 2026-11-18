package com.pawmento.map.adapter.in.web;

import java.time.Instant;

import com.pawmento.map.application.port.in.EndWalkResult;

record EndWalkResponse(Long walkId, Instant endedAt, String status) {

	static EndWalkResponse from(EndWalkResult result) {
		return new EndWalkResponse(result.walkId(), result.endedAt(), result.status());
	}

}
