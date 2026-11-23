package com.pawmento.map.application.service;

import org.springframework.stereotype.Service;

import com.pawmento.map.application.port.in.ListPlacesQuery;
import com.pawmento.map.application.port.in.ListPlacesResult;
import com.pawmento.map.application.port.in.ListPlacesUseCase;
import com.pawmento.map.application.port.out.LoadPlacesPort;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceSelection;

import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ListPlacesService implements ListPlacesUseCase {

	// 한 번에 돌려주는 장소의 최대 개수. 넘으면 잘렸다고 알려 준다
	private static final int MAX_PLACES = 500;

	private final LoadPlacesPort loadPlacesPort;

	@Override
	public ListPlacesResult list(ListPlacesQuery query) {
		if (query.categories().isEmpty()) {
			throw new IllegalArgumentException("종류가 하나 이상 필요합니다.");
		}
		MapArea area = MapArea.of(query.minLatitude(), query.minLongitude(), query.maxLatitude(),
				query.maxLongitude());
		List<Place> candidates = loadPlacesPort.loadInArea(area, query.categories());
		PlaceSelection.Selected selected = PlaceSelection.select(candidates, area, query.categories(), MAX_PLACES);
		return new ListPlacesResult(selected.places(), selected.truncated());
	}

}
