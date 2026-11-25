package com.pawmento.map.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.pawmento.map.domain.PlaceCategory;

public interface PlaceSyncStatusPort {

	/** 진행 중이 아닐 때만 진행 중으로 바꾸고 true를 돌려준다. 이미 진행 중이면 false다(요청 두 개가 동시에 들어와도 하나만 시작). */
	boolean tryStart(PlaceCategory category, Instant startedAt);

	void finish(PlaceCategory category, PlaceSyncStatus status);

	Optional<PlaceSyncStatus> find(PlaceCategory category);

}
