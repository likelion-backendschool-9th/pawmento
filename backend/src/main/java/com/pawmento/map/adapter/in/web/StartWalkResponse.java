package com.pawmento.map.adapter.in.web;

import java.time.Instant;

import com.pawmento.map.application.port.in.StartWalkResult;

record StartWalkResponse(Long walkId, Instant startedAt, String status) {

	static StartWalkResponse from(StartWalkResult result) {
		return new StartWalkResponse(result.walkId(), result.startedAt(), result.status());
	}

}
