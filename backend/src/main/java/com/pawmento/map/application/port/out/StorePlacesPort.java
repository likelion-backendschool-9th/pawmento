package com.pawmento.map.application.port.out;

import java.util.List;

import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

public interface StorePlacesPort {

	/** 저장된 한 종류의 장소 수. */
	int countByCategory(PlaceCategory category);

	/**
	 * 한 종류의 장소를 drafts로 통째로 맞춘다. 같은 원본 관리번호는 값이 바뀐 것만 갱신하고, 없던 것은 추가하고, 이번 목록에 없는
	 * 같은 종류의 장소는 지운다. 다른 종류는 건드리지 않는다. 추가·갱신·삭제는 한 트랜잭션이라 도중에 실패하면 하나도 적용되지 않는다.
	 */
	AppliedPlaces replace(PlaceCategory category, List<PlaceDraft> drafts);

}
