package com.pawmento.map.application.service;

import com.pawmento.map.domain.PlaceCategory;

final class PlaceSyncCategories {

	private PlaceSyncCategories() {
	}

	// 병원과 미용만 이 API로 가져온다. 웹 어댑터가 이름을 검사하고 오므로 그 밖의 값은 호출한 쪽의 버그다
	static PlaceCategory toCategory(String name) {
		if ("HOSPITAL".equals(name)) {
			return PlaceCategory.HOSPITAL;
		}
		if ("GROOMING".equals(name)) {
			return PlaceCategory.GROOMING;
		}
		throw new IllegalArgumentException("가져올 수 없는 종류입니다: " + name);
	}

}
