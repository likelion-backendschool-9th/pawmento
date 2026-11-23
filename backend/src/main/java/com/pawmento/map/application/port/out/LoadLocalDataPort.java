package com.pawmento.map.application.port.out;

import com.pawmento.map.domain.PlaceCategory;

public interface LoadLocalDataPort {

	/**
	 * 한 종류(병원 또는 미용)의 공공데이터를 페이지 끝까지 받는다. 중간 페이지 호출이 실패하거나 시간 초과하면 일부만 돌려주지 않고
	 * LocalDataSyncFailedException(FETCH_FAILED)을 던진다.
	 */
	LocalDataSnapshot load(PlaceCategory category);

}
