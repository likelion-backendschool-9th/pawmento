package com.pawmento.map.application.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.pawmento.map.application.port.in.ListPlacesQuery;
import com.pawmento.map.application.port.in.ListPlacesResult;
import com.pawmento.map.application.port.in.ListPlacesUseCase;
import com.pawmento.map.application.port.out.LoadPlacesPort;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceSelection;

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
		Set<PlaceCategory> categories = toCategories(query.categories());
		MapArea area = MapArea.of(query.minLatitude(), query.minLongitude(), query.maxLatitude(),
				query.maxLongitude());
		List<Place> candidates = loadPlacesPort.loadInArea(area, categories);
		PlaceSelection.Selected selected = PlaceSelection.select(candidates, area, categories, MAX_PLACES);
		return new ListPlacesResult(selected.places().stream().map(ListPlacesService::toItem).toList(),
				selected.truncated());
	}

	// 웹 어댑터가 이름을 검사한 뒤에 오므로 모르는 이름은 호출한 쪽의 버그다(IllegalArgumentException)
	private static Set<PlaceCategory> toCategories(Set<String> names) {
		Set<PlaceCategory> categories = EnumSet.noneOf(PlaceCategory.class);
		names.forEach(name -> categories.add(PlaceCategory.valueOf(name)));
		return categories;
	}

	private static ListPlacesResult.Item toItem(Place place) {
		return new ListPlacesResult.Item(place.id(), place.category().name(), place.name(), place.address(),
				place.phone(), place.latitude(), place.longitude(), place.open24h(),
				place.open24hBasis() == null ? null : place.open24hBasis().name(), place.openingHours(),
				place.closedDays());
	}

}
