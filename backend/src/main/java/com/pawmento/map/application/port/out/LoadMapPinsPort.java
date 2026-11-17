package com.pawmento.map.application.port.out;

import java.util.List;

import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.RecordLocation;

public interface LoadMapPinsPort {

	/**
	 * 회원이 만든 위치 중, 반려동물 하나의 영역 안에 있는 것을 오래된 순(id 순)으로 돌려준다.
	 * 영역의 경계선 위에 있는 위치도 포함한다. 최대 limit개까지만 돌려준다.
	 */
	List<RecordLocation> loadInArea(Long createdBy, Long petId, MapArea area, int limit);

}
