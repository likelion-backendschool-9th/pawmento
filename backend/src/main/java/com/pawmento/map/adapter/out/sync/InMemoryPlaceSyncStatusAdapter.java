package com.pawmento.map.adapter.out.sync;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pawmento.map.application.port.out.PlaceSyncStatus;
import com.pawmento.map.application.port.out.PlaceSyncStatusPort;
import com.pawmento.map.domain.PlaceCategory;

// 상태는 이 서버의 메모리에만 둔다. 서버가 여러 대면 대수마다 따로 가진다(같은 종류의 중복 실행은 DB 잠금이 막는다)
@Component
class InMemoryPlaceSyncStatusAdapter implements PlaceSyncStatusPort {

	private final Map<PlaceCategory, PlaceSyncStatus> statuses = new EnumMap<>(PlaceCategory.class);

	@Override
	public synchronized boolean tryStart(PlaceCategory category, Instant startedAt) {
		PlaceSyncStatus current = statuses.get(category);
		if (current != null && current.state() == PlaceSyncStatus.State.RUNNING) {
			return false;
		}
		statuses.put(category, PlaceSyncStatus.running(startedAt));
		return true;
	}

	@Override
	public synchronized void finish(PlaceCategory category, PlaceSyncStatus status) {
		statuses.put(category, status);
	}

	@Override
	public synchronized Optional<PlaceSyncStatus> find(PlaceCategory category) {
		return Optional.ofNullable(statuses.get(category));
	}

}
