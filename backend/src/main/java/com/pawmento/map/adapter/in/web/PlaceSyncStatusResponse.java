package com.pawmento.map.adapter.in.web;

import java.time.Instant;

import com.pawmento.map.application.port.in.PlaceSyncStatusResult;

record PlaceSyncStatusResponse(String category, String state, Instant startedAt, Instant finishedAt,
		SyncPlacesResponse result, String failure) {

	static PlaceSyncStatusResponse from(PlaceSyncStatusResult status) {
		return new PlaceSyncStatusResponse(status.category(), status.state(), status.startedAt(), status.finishedAt(),
				status.result() == null ? null : SyncPlacesResponse.from(status.result()), status.failure());
	}

}
