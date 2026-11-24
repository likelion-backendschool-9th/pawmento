package com.pawmento.map.application.port.out;

public interface PlaceSyncExecutorPort {

	/** 가져오기를 요청과 따로 백그라운드에서 돌린다. 받아들이지 못하면 예외를 던진다. */
	void execute(Runnable task);

}
