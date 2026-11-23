package com.pawmento.map.adapter.out.persistence;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.pawmento.map.application.exception.PlacePersistenceUnavailableException;
import com.pawmento.map.application.port.out.LoadPlacesPort;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class PlacePersistenceAdapter implements LoadPlacesPort {

	private final PlaceJpaRepository placeJpaRepository;

	@Override
	public List<Place> loadInArea(MapArea area, Set<PlaceCategory> categories) {
		if (area == null || categories == null || categories.isEmpty()) {
			throw new IllegalArgumentException("영역과 종류가 필요합니다.");
		}
		return MapPersistenceOperations.execute(() -> placeJpaRepository
			.findInArea(categories, area.getMinLatitude(), area.getMinLongitude(), area.getMaxLatitude(),
					area.getMaxLongitude())
			.stream()
			.map(PlaceJpaEntity::toDomain)
			.toList(), PlacePersistenceUnavailableException::new);
	}

}
