package com.pawmento.map.application.service;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.ListPlacesQuery;
import com.pawmento.map.application.port.in.ListPlacesResult;
import com.pawmento.map.application.port.in.ListPlacesUseCase;
import com.pawmento.map.application.port.out.LoadPlaceDataAsOfPort;
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

	private final LoadPlaceDataAsOfPort loadPlaceDataAsOfPort;

	/**
	 * 장소 목록과 가져온 시각을 같은 읽기 스냅샷(REPEATABLE READ)에서 읽는다. 기본 격리 수준에서는 두 조회 사이에 가져오기가 커밋되면
	 * 이전 장소 목록에 새 가져온 시각이 붙어 데이터가 실제보다 새것처럼 보인다. 이미 열린 트랜잭션 안에서 부르면 격리 수준 지정이
	 * 무시되므로 새 읽기 트랜잭션을 시작한다. 장소를 먼저 읽어 스냅샷이 거기서 시작되게 한다(#172-S21).
	 */
	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
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
				selected.truncated(), dataAsOf(selected.places(), categories));
	}

	// 가져온 시각을 정하는 규칙은 장소 검색과 같다(PlaceDataAsOf, #172-S21)
	private Instant dataAsOf(List<Place> places, Set<PlaceCategory> requested) {
		return PlaceDataAsOf.of(loadPlaceDataAsOfPort, places, requested);
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
