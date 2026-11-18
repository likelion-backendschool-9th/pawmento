package com.pawmento.map.application.port.out;

import com.pawmento.map.domain.Walk;

public interface SaveWalkPort {

	/**
	 * 산책을 저장하고 id를 돌려준다. id가 없으면 새로 만들고, 있으면 끝낸 시각과 마지막 활동 시각을 바꾼다.
	 * 반려동물·작성자·시작 시각은 바뀌지 않는다.
	 */
	Long save(Walk walk);

}
