package com.pawmento.map.adapter.out.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.exception.PlacePersistenceUnavailableException;
import com.pawmento.map.application.port.out.PlaceSyncLockPort;
import com.pawmento.map.domain.PlaceCategory;

import lombok.RequiredArgsConstructor;

/**
 * PostgreSQL 어드바이저리 락으로 가져오기를 한 종류에 하나씩만 돌린다. 락은 DB 연결에 붙어 있어서 서버가 여러 대여도 하나만 잡고,
 * 서버가 죽거나 연결이 끊기면 DB가 알아서 놓는다(영영 막히는 일이 없다). 락을 잡은 연결은 가져오는 동안 따로 쥐고 있다가
 * 놓을 때 풀에 돌려준다. 가져오기의 트랜잭션과는 별개의 연결이다(#172-S22).
 */
@Component
@RequiredArgsConstructor
class PlaceSyncLockAdapter implements PlaceSyncLockPort {

	private static final Logger log = LoggerFactory.getLogger(PlaceSyncLockAdapter.class);

	// 다른 기능의 어드바이저리 락과 겹치지 않게 정한 이 기능의 구분 번호('PLCS')
	private static final int NAMESPACE = 0x504C4353;

	private final DataSource dataSource;

	@Override
	public Optional<SyncLock> tryAcquire(PlaceCategory category) {
		Connection connection = null;
		try {
			connection = dataSource.getConnection();
			connection.setAutoCommit(true);
			boolean acquired;
			try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?, ?)")) {
				statement.setInt(1, NAMESPACE);
				statement.setInt(2, category.ordinal());
				try (ResultSet result = statement.executeQuery()) {
					acquired = result.next() && result.getBoolean(1);
				}
			}
			if (!acquired) {
				connection.close();
				return Optional.empty();
			}
			return Optional.of(new HeldLock(connection, category));
		}
		catch (SQLException e) {
			closeQuietly(connection);
			throw new PlacePersistenceUnavailableException(e);
		}
	}

	private static void closeQuietly(Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			connection.close();
		}
		catch (SQLException ignored) {
			// 이미 실패를 알리는 중이라 닫기 오류는 더하지 않는다
		}
	}

	private static final class HeldLock implements SyncLock {

		private final Connection connection;

		private final PlaceCategory category;

		private final AtomicBoolean released = new AtomicBoolean();

		HeldLock(Connection connection, PlaceCategory category) {
			this.connection = connection;
			this.category = category;
		}

		@Override
		public void close() {
			if (!released.compareAndSet(false, true)) {
				return;
			}
			// 락을 못 풀어도 연결을 닫으면 DB가 락을 놓는다. 그래서 풀기에 실패해도 연결은 꼭 닫는다
			try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?, ?)")) {
				statement.setInt(1, NAMESPACE);
				statement.setInt(2, category.ordinal());
				statement.execute();
			}
			catch (SQLException e) {
				log.warn("장소 가져오기 잠금을 풀지 못해 연결을 닫아 놓습니다: {}", category, e);
			}
			finally {
				closeQuietly(connection);
			}
		}

	}

}
