package com.pawmento.map.application.port.out;

import java.util.Optional;

import com.pawmento.map.domain.PlaceCategory;

/**
 * 장소 가져오기를 한 종류에 하나씩만 돌리는 잠금. 서버가 여러 대여도, 관리자 수동 실행과 자동 실행이 겹쳐도 같은 종류를 두 번
 * 동시에 받지 않게 한다(#172-S22).
 */
public interface PlaceSyncLockPort {

	/**
	 * 잠금을 기다리지 않고 잡는다. 이미 다른 곳이 잡고 있으면 빈 값이다. 잡았으면 반드시 닫아서 놓아야 한다.
	 */
	Optional<SyncLock> tryAcquire(PlaceCategory category);

	/** 잡은 잠금. close()는 여러 번 불러도 한 번만 놓는다. */
	interface SyncLock extends AutoCloseable {

		/**
		 * 이 잠금을 아직 쥐고 있는지 확인한다. 가져오는 데 몇 분이 걸려서 그동안 DB 연결이 끊기면 잠금이 풀리고 다른 실행이 같은
		 * 종류를 시작할 수 있다. 저장하기 직전에 불러서, 잠금을 잃었으면 오래된 스냅샷으로 덮어쓰지 않고 중단한다.
		 *
		 * @throws com.pawmento.map.application.exception.PlaceSyncLockLostException 잠금을 잃었을 때
		 */
		default void requireHeld() {
		}

		@Override
		void close();

	}

}
