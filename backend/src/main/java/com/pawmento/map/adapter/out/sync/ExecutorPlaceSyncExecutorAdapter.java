package com.pawmento.map.adapter.out.sync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.port.out.PlaceSyncExecutorPort;

// 수동 가져오기 전용 스레드 하나. 공용 스케줄러(미디어·엽서)를 몇 분씩 붙잡지 않고, 수동 실행끼리도 한 번에 하나씩만 돈다
@Component
class ExecutorPlaceSyncExecutorAdapter implements PlaceSyncExecutorPort, DisposableBean {

	private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "place-sync-manual");
		thread.setDaemon(true);
		return thread;
	});

	@Override
	public void execute(Runnable task) {
		executor.execute(task);
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}

}
