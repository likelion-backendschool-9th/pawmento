package com.pawmento.map.adapter.in.web;

import com.pawmento.map.application.port.in.SyncLocalDataPlacesResult;

record SyncPlacesResponse(String category, int read, int saved, int excluded, int closed, int noCoordinates,
		int outOfRange, int inserted, int updated, int deleted) {

	static SyncPlacesResponse from(SyncLocalDataPlacesResult result) {
		return new SyncPlacesResponse(result.category(), result.read(), result.saved(), result.excluded(),
				result.closed(), result.noCoordinates(), result.outOfRange(), result.inserted(), result.updated(),
				result.deleted());
	}

}
