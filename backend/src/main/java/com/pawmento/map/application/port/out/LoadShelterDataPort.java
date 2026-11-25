package com.pawmento.map.application.port.out;

public interface LoadShelterDataPort {

	/**
	 * 보호센터를 받는다. 호출이 실패하거나 시간 초과하면 일부만 돌려주지 않고 PublicDataSyncFailedException(FETCH_FAILED)을
	 * 던진다.
	 */
	ShelterSnapshot load();

}
