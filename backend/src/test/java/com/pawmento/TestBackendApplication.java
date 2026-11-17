package com.pawmento;

import org.springframework.boot.SpringApplication;

// ./gradlew bootTestRun: compose.yaml 대신 Testcontainers의 DB로 앱을 실행한다
public class TestBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
