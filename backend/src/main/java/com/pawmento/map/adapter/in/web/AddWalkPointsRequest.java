package com.pawmento.map.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.pawmento.map.application.port.in.AddWalkPointsCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 형식(값이 있는지, 위도·경도 범위)은 여기서 보고, 묶음이 비었거나 100개를 넘는지는 도메인(Walk)이 본다
record AddWalkPointsRequest(
		@NotNull(message = "points를 입력해 주세요.")
		List<@NotNull(message = "points에 빈 항목이 있습니다.") @Valid PointRequest> points) {

	record PointRequest(
			@NotNull(message = "latitude를 입력해 주세요.") @Latitude Double latitude,
			@NotNull(message = "longitude를 입력해 주세요.") @Longitude Double longitude,
			@NotNull(message = "recordedAt을 입력해 주세요.") Instant recordedAt) {
	}

	AddWalkPointsCommand toCommand(Long memberId, Long walkId) {
		List<AddWalkPointsCommand.Point> converted = points.stream()
			.map(point -> new AddWalkPointsCommand.Point(point.latitude(), point.longitude(), point.recordedAt()))
			.toList();
		return new AddWalkPointsCommand(memberId, walkId, converted);
	}

}
