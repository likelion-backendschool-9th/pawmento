package com.pawmento.map.adapter.in.scheduling;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;

// 가져오기 전용 스레드 하나에서 돌린다. 앱의 공용 스케줄러는 스레드가 1개라 미디어·엽서 작업과 나눠 쓰는데, 가져오기는 몇 분 걸려서
// 거기서 돌리면 그동안 사진·엽서 처리가 멈춘다. 스레드가 하나라 자동 실행끼리도 겹치지 않는다
final class ExecutorPlaceSyncRunner implements PlaceSyncRunner, DisposableBean {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "place-sync");
		thread.setDaemon(true);
		return thread;
	});

	@Override
	public void runNow(Runnable task) {
		executor.execute(task);
	}

	@Override
	public void runLater(Runnable task, Duration delay) {
		executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}

}
