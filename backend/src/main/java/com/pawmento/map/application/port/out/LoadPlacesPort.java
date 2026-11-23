package com.pawmento.map.application.port.out;

import java.util.List;
import java.util.Set;

import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

public interface LoadPlacesPort {

	/** 영역 안(경계선 포함)에 있는 요청한 종류의 장소를 모두 돌려준다. 순서는 정하지 않는다. */
	List<Place> loadInArea(MapArea area, Set<PlaceCategory> categories);

}
