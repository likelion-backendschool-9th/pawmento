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
import com.pawmento.map.application.exception.PlaceSyncLockLostException;
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

	// 연결 풀의 연결은 close()를 해도 물리 연결이 살아 있어서 세션에 걸린 어드바이저리 락이 그대로 남는다. 락을 풀었는지 확인하지
	// 못한 연결은 물리 연결까지 끊어서(abort, 풀에서도 빠진다) 락이 풀 안에 갇히지 않게 한다
	private static void discard(Connection connection) {
		try {
			connection.abort(Runnable::run);
		}
		catch (SQLException | RuntimeException e) {
			log.warn("잠금을 풀지 못한 연결을 폐기하지 못했습니다", e);
			closeQuietly(connection);
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

		// 잠금을 잡은 그 연결(세션)이 아직 이 잠금의 주인인지 pg_locks로 확인한다. 연결이 끊겼으면 질의 자체가 실패한다
		@Override
		public void requireHeld() {
			try (PreparedStatement statement = connection.prepareStatement(
					"select count(*) from pg_locks where locktype = 'advisory' and classid = ?::oid and objid = ?::oid "
							+ "and objsubid = 2 and pid = pg_backend_pid() and granted")) {
				statement.setInt(1, NAMESPACE);
				statement.setInt(2, category.ordinal());
				try (ResultSet result = statement.executeQuery()) {
					if (result.next() && result.getLong(1) == 1) {
						return;
					}
				}
				throw new PlaceSyncLockLostException(null);
			}
			catch (SQLException e) {
				throw new PlaceSyncLockLostException(e);
			}
		}

		@Override
		public void close() {
			if (!released.compareAndSet(false, true)) {
				return;
			}
			boolean unlocked = false;
			try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?, ?)")) {
				statement.setInt(1, NAMESPACE);
				statement.setInt(2, category.ordinal());
				try (ResultSet result = statement.executeQuery()) {
					unlocked = result.next() && result.getBoolean(1);
				}
			}
			catch (SQLException e) {
				log.warn("장소 가져오기 잠금을 풀지 못해 연결을 폐기합니다: {}", category, e);
				discard(connection);
				return;
			}
			if (!unlocked) {
				// 이미 잠금을 쥐고 있지 않았다(끊겼다 다시 붙는 등). 풀 안에 남길 락은 없다
				log.warn("장소 가져오기 잠금이 이미 풀려 있었습니다: {}", category);
			}
			closeQuietly(connection);
		}

	}

}
