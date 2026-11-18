package com.pawmento.map.application.port.out;

import com.pawmento.map.domain.Walk;

public interface SaveWalkPort {

	/**
	 * 산책을 저장하고 id를 돌려준다. id가 없으면 새로 만들고, 있으면 끝낸 시각과 마지막 활동 시각을 바꾼다.
	 * 반려동물·작성자·시작 시각은 바뀌지 않고, 이미 정해진 끝낸 시각도 다른 값으로 바꾸지 않는다.
	 *
	 * <p>이미 있는 산책의 상태를 바꿔 저장할 때는 {@link LoadWalkPort#loadByIdForUpdate}로 읽은 같은 트랜잭션에서 부른다.
	 * 일반 조회로 읽은 값을 나중에 저장하면 그 사이에 다른 요청이 바꾼 값을 덮을 수 있다.
	 */
	Long save(Walk walk);

}
