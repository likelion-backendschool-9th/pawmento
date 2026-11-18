package com.pawmento.map.adapter.out.persistence;

import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;

import jakarta.persistence.PersistenceException;

// 저장소 호출에서 나온 JPA·Spring 데이터 접근 예외를 응용 예외로 바꾼다. 원인은 cause로 보존하고,
// 우리가 던진 예외(IllegalStateException, 규칙 위반, 이미 있음 등)와 프로그래밍 실수는 그대로 지나간다
final class MapPersistenceOperations {

	private MapPersistenceOperations() {
	}

	static <T> T execute(Supplier<T> action, Function<Throwable, RuntimeException> unavailable) {
		try {
			return action.get();
		}
		catch (PersistenceException | DataAccessException e) {
			throw unavailable.apply(e);
		}
	}

	static void execute(Runnable action, Function<Throwable, RuntimeException> unavailable) {
		execute(() -> {
			action.run();
			return null;
		}, unavailable);
	}

}
