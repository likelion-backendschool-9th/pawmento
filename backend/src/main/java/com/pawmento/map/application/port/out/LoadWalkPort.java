package com.pawmento.map.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.pawmento.map.domain.Walk;

public interface LoadWalkPort {

	Optional<Walk> loadById(Long walkId);

	/**
	 * 산책을 읽으면서 행을 잠근다. 같은 산책에 좌표를 쌓는 요청이 동시에 와도 개수 한도를 지키려는 용도이고,
	 * 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다.
	 */
	Optional<Walk> loadByIdForUpdate(Long walkId);

	/**
	 * 회원이 만든 반려동물의 산책 중 시작한 시각이 [from, to)에 드는 것을 시작 시각 순으로 돌려준다.
	 * 날짜를 나누는 기준(Asia/Seoul)은 호출하는 쪽이 정한다.
	 */
	List<Walk> loadStartedBetween(Long createdBy, Long petId, Instant from, Instant to);

	/**
	 * 반려동물에게 끝내지 않은 산책이 있고, 그 산책이 마지막으로 활동한 시각이 기준 시각과 같거나 나중인지 알려 준다.
	 * 진행 중인 산책이 있는지 알아낼 때 쓴다. 기준 시각은 지금에서 중단 시간을 뺀 값이고, 정확히 그 시각에 활동한 산책은
	 * 아직 진행 중이므로 기준 시각을 포함한다(Walk#status와 같은 기준).
	 */
	boolean existsOpenWalkActiveSince(Long petId, Instant activeSince);

}
