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

		@Override
		void close();

	}

}
