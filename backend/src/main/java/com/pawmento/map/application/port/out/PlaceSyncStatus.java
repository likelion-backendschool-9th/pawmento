package com.pawmento.map.application.port.out;

import java.time.Instant;

import com.pawmento.map.application.port.in.SyncPublicDataPlacesResult;

/** 한 종류의 마지막 가져오기 상태. */
public record PlaceSyncStatus(State state, Instant startedAt, Instant finishedAt, SyncPublicDataPlacesResult result,
		String failure) {

	public enum State {

		RUNNING, SUCCEEDED, FAILED

	}

	public static PlaceSyncStatus running(Instant startedAt) {
		return new PlaceSyncStatus(State.RUNNING, startedAt, null, null, null);
	}

	public static PlaceSyncStatus succeeded(Instant startedAt, Instant finishedAt, SyncPublicDataPlacesResult result) {
		return new PlaceSyncStatus(State.SUCCEEDED, startedAt, finishedAt, result, null);
	}

	public static PlaceSyncStatus failed(Instant startedAt, Instant finishedAt, String failure) {
		return new PlaceSyncStatus(State.FAILED, startedAt, finishedAt, null, failure);
	}

}
