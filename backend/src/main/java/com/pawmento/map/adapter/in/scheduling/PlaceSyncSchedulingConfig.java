package com.pawmento.map.adapter.in.scheduling;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.pawmento.map.application.port.in.SyncLocalDataPlacesUseCase;

// 매일 자동 가져오기. 인증키가 없으면(로컬·테스트) 만들지 않고, pawmento.map.local-data.sync.enabled=false로 끌 수 있다
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression("'${pawmento.map.local-data.service-key:}' != '' and ${pawmento.map.local-data.sync.enabled:true}")
class PlaceSyncSchedulingConfig {

	@Bean
	ExecutorPlaceSyncRunner placeSyncRunner() {
		return new ExecutorPlaceSyncRunner();
	}

	@Bean
	PlaceSyncScheduler placeSyncScheduler(SyncLocalDataPlacesUseCase useCase, ExecutorPlaceSyncRunner runner,
			@Value("${pawmento.map.local-data.sync.retry-delay:30m}") Duration retryDelay,
			@Value("${pawmento.map.local-data.sync.max-retries:3}") int maxRetries) {
		return new PlaceSyncScheduler(useCase, runner, retryDelay, maxRetries);
	}

}
