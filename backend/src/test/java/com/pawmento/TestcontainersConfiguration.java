package com.pawmento;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 JVM 하나에 Postgres 컨테이너를 하나만 띄우고, Spring 컨텍스트마다 새 데이터베이스를 만들어 준다.
 *
 * <p>컨테이너를 {@code @Bean}으로 두면 컨텍스트가 새로 뜰 때마다 컨테이너도 새로 떠서 기동 시간이
 * 테스트 클래스 수만큼 반복된다. 그렇다고 한 데이터베이스를 같이 쓰면 앱이 시작할 때 도는 스키마 스크립트와
 * 백필 검사가 다른 테스트가 남긴 데이터를 보게 된다. 그래서 컨테이너만 같이 쓰고, 컨텍스트는 예전처럼
 * 빈 데이터베이스에서 시작한다.
 *
 * <p>컨테이너는 Spring 빈이 아니라 static 필드다. 컨텍스트 캐시가 컨텍스트를 내보낼 때 컨테이너가 같이
 * 멈추지 않게 하기 위해서다. 종료는 Testcontainers(Ryuk)가 JVM이 끝날 때 한다.
 *
 * <p>캐시에 남은 컨텍스트들이 같은 서버에 연결하므로 유휴 연결을 줄이고(testcontainers.properties)
 * 연결 한도를 기본값 100보다 넉넉히 둔다.
 */
@TestConfiguration(proxyBeanMethods = false)
@PropertySource("classpath:testcontainers.properties")
public class TestcontainersConfiguration {

	private static final AtomicInteger DATABASES = new AtomicInteger();

	// 처음 쓸 때 띄운다. static 초기화에서 띄우면 실패했을 때 ExceptionInInitializerError, 이후에는
	// NoClassDefFoundError로만 보여 원인을 찾기 어렵다. 실패하면 다음 컨텍스트가 다시 시도하고 같은 원인을 보여 준다.
	private static PostgreSQLContainer postgres;

	@Bean
	JdbcConnectionDetails postgresConnectionDetails() {
		PostgreSQLContainer container = postgres();
		String url = createDatabase(container, "context_" + DATABASES.incrementAndGet());
		return new JdbcConnectionDetails() {
			@Override
			public String getJdbcUrl() {
				return url;
			}

			@Override
			public String getUsername() {
				return container.getUsername();
			}

			@Override
			public String getPassword() {
				return container.getPassword();
			}
		};
	}

	private static synchronized PostgreSQLContainer postgres() {
		if (postgres == null) {
			// compose.yaml과 같은 이미지를 쓴다
			PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
				.withCommand("postgres", "-c", "max_connections=400");
			try {
				container.start();
			}
			catch (RuntimeException e) {
				throw new IllegalStateException(
						"테스트용 Postgres 컨테이너를 띄우지 못했습니다. Docker가 켜져 있는지, postgres:18-alpine 이미지를 받을 수 있는지 확인하세요.",
						e);
			}
			postgres = container;
		}
		return postgres;
	}

	private static String createDatabase(PostgreSQLContainer container, String name) {
		try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(),
				container.getPassword()); Statement statement = connection.createStatement()) {
			statement.execute("create database " + name);
		}
		catch (SQLException e) {
			throw new IllegalStateException("테스트 데이터베이스를 만들지 못했습니다: " + name, e);
		}
		return "jdbc:postgresql://" + container.getHost() + ":"
				+ container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + name;
	}

}
