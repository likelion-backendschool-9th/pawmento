package com.pawmento.map.adapter.in.scheduling;

import java.time.Duration;

// 가져오기 작업을 어느 스레드에서 언제 돌릴지 정한다. 테스트는 바로 돌리는 가짜를 쓴다
interface PlaceSyncRunner {

	/** 지금 돌린다. */
	void runNow(Runnable task);

	/** 정한 시간 뒤에 돌린다. */
	void runLater(Runnable task, Duration delay);

}
