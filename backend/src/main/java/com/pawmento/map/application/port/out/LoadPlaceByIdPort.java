package com.pawmento.map.application.port.out;

import java.util.Optional;

import com.pawmento.map.domain.Place;

public interface LoadPlaceByIdPort {

	/** 장소 하나를 id로 읽는다. 없으면 빈 값이다. */
	Optional<Place> loadById(Long placeId);

}
