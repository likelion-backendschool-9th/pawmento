package com.pawmento.map.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.pawmento.map.application.port.in.ListMapPinsQuery;
import com.pawmento.map.application.port.in.ListMapPinsResult;
import com.pawmento.map.application.port.in.ListMapPinsUseCase;
import com.pawmento.map.application.port.out.LoadMapPinsPort;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.DiaryLocation;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ListMapPinsService implements ListMapPinsUseCase {

	// 한 번에 돌려주는 핀의 최대 개수. 넘으면 잘렸다고 알려 준다
	private static final int MAX_PINS = 500;

	private final LoadMapPinsPort loadMapPinsPort;

	@Override
	public ListMapPinsResult list(ListMapPinsQuery query) {
		MapArea area = toArea(query);
		// 하나를 더 읽어, 최대 개수를 넘는 핀이 있는지 알아낸다
		List<DiaryLocation> found = loadMapPinsPort.loadInArea(query.memberId(), query.petId(), area, MAX_PINS + 1);
		boolean truncated = found.size() > MAX_PINS;
		List<ListMapPinsResult.Pin> pins = found.stream()
			.limit(MAX_PINS)
			.map(location -> new ListMapPinsResult.Pin(location.getDiaryId(), location.getLatitude(),
					location.getLongitude(), location.getPlaceName()))
			.toList();
		return new ListMapPinsResult(pins, truncated);
	}

	private MapArea toArea(ListMapPinsQuery query) {
		return MapArea.of(query.minLatitude(), query.minLongitude(), query.maxLatitude(), query.maxLongitude());
	}

}
