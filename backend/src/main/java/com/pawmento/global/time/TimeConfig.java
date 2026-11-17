package com.pawmento.global.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

}
