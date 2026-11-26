package com.pawmento.map.application.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.SearchPlacesQuery;
import com.pawmento.map.application.port.in.SearchPlacesResult;
import com.pawmento.map.application.port.in.SearchPlacesUseCase;
import com.pawmento.map.application.port.out.LoadPlaceDataAsOfPort;
import com.pawmento.map.application.port.out.LoadPlacesByKeywordPort;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceSearch;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class SearchPlacesService implements SearchPlacesUseCase {

	// 한 번에 돌려주는 장소의 최대 개수. 넘으면 잘렸다고 알려 준다(#195-S5)
	private static final int MAX_PLACES = 20;

	private final LoadPlacesByKeywordPort loadPlacesByKeywordPort;

	private final LoadPlaceDataAsOfPort loadPlaceDataAsOfPort;

	/**
	 * 장소 목록과 가져온 시각을 같은 읽기 스냅샷(REPEATABLE READ)에서 읽는다. 장소 목록 조회(ListPlacesService)와 같은 이유다:
	 * 두 조회 사이에 가져오기가 커밋되면 이전 장소에 새 가져온 시각이 붙어 데이터가 실제보다 새것처럼 보인다.
	 * 검색어는 장소와 비교만 하고 로그에 남기지 않는다(#195).
	 */
	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
	public SearchPlacesResult search(SearchPlacesQuery query) {
		if (query.categories().isEmpty()) {
			throw new IllegalArgumentException("종류가 하나 이상 필요합니다.");
		}
		String key = PlaceSearch.normalize(query.query());
		Set<PlaceCategory> categories = toCategories(query.categories());
		Optional<PlaceSearch.Center> center = query.latitude() == null || query.longitude() == null
				? Optional.empty()
				: Optional.of(new PlaceSearch.Center(query.latitude().doubleValue(), query.longitude().doubleValue()));

		List<Place> candidates = loadPlacesByKeywordPort.loadByKeyword(key, categories);
		PlaceSearch.Result selected = PlaceSearch.select(candidates, key, categories, center, MAX_PLACES);
		List<SearchPlacesResult.Item> items = selected.hits().stream().map(SearchPlacesService::toItem).toList();
		Instant dataAsOf = PlaceDataAsOf.of(loadPlaceDataAsOfPort,
				selected.hits().stream().map(PlaceSearch.Hit::place).toList(), categories);
		return new SearchPlacesResult(items, selected.truncated(), dataAsOf);
	}

	// 웹 어댑터가 이름을 검사한 뒤에 오므로 모르는 이름은 호출한 쪽의 버그다(IllegalArgumentException)
	private static Set<PlaceCategory> toCategories(Set<String> names) {
		Set<PlaceCategory> categories = EnumSet.noneOf(PlaceCategory.class);
		names.forEach(name -> categories.add(PlaceCategory.valueOf(name)));
		return categories;
	}

	private static SearchPlacesResult.Item toItem(PlaceSearch.Hit hit) {
		Place place = hit.place();
		return new SearchPlacesResult.Item(place.id(), place.category().name(), place.name(), place.address(),
				place.phone(), place.latitude(), place.longitude(), place.open24h(),
				place.open24hBasis() == null ? null : place.open24hBasis().name(), place.openingHours(),
				place.closedDays(), hit.distanceMeters());
	}

}
