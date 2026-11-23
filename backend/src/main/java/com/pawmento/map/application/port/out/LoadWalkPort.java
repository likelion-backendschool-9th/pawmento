package com.pawmento.map.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.pawmento.map.domain.Walk;

public interface LoadWalkPort {

	// 읽기만 하는 용도다. 읽은 산책을 바꿔 저장하려면 loadByIdForUpdate를 쓴다
	Optional<Walk> loadById(Long walkId);

	/**
	 * 산책의 반려동물과 시작한 회원만 잠그지 않고 읽는다. 이 둘은 바뀌지 않는 값이라 잠그기 전에 읽어도 된다.
	 * 상태를 함께 읽지 않으므로, 이 값으로 상태를 판단하거나 산책을 바꿔 저장하지 말고 {@link #loadByIdForUpdate}로 다시 읽는다.
	 */
	Optional<WalkOwnership> loadOwnership(Long walkId);

	/**
	 * 산책을 읽으면서 행을 잠근다. 같은 산책에 좌표를 쌓거나 끝내는 요청이 동시에 와도 개수 한도와 먼저 정해진
	 * 끝낸 시각을 지키려는 용도이고, 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다.
	 * 산책의 상태를 바꾸는 모든 요청(좌표 쌓기, 끝내기)은 이 메서드로 읽고, 같은 트랜잭션에서 {@link SaveWalkPort#save}까지 끝낸다.
	 * 같은 트랜잭션에서 이미 읽어 둔 산책이 있어도 잠근 뒤의 최신 상태를 돌려준다(다른 트랜잭션이 그사이 커밋한 끝난 시각과 마지막 활동 시각을 반영한다).
	 */
	Optional<Walk> loadByIdForUpdate(Long walkId);

	/**
	 * 회원이 만든 반려동물의 산책 중 시작한 시각이 [from, to)에 드는 것을 시작 시각 순으로 돌려준다.
	 * 날짜를 나누는 기준(Asia/Seoul)은 호출하는 쪽이 정한다.
	 */
	List<Walk> loadStartedBetween(Long createdBy, Long petId, Instant from, Instant to);

	/**
	 * 회원이 반려동물과 하고 있는 산책 중 끝내지 않았고 마지막으로 활동한 시각이 기준 시각과 같거나 나중인 것을 읽는다.
	 * 지금 진행 중인 산책을 찾을 때 쓰며 기준 시각은 지금에서 중단 시간을 뺀 값이다(Walk#status와 같은 기준이라 정확히
	 * 그 시각에 활동한 산책을 포함한다). 시작한 날짜와 상관없다. 반려동물마다 진행 중인 산책은 하나뿐이지만, 둘이 있어도
	 * 가장 늦게 시작한 것 하나를 돌려준다.
	 */
	Optional<Walk> loadOpenWalkActiveSince(Long createdBy, Long petId, Instant activeSince);

	/**
	 * 반려동물에게 끝내지 않은 산책이 있고, 그 산책이 마지막으로 활동한 시각이 기준 시각과 같거나 나중인지 알려 준다.
	 * 진행 중인 산책이 있는지 알아낼 때 쓴다. 기준 시각은 지금에서 중단 시간을 뺀 값이고, 정확히 그 시각에 활동한 산책은
	 * 아직 진행 중이므로 기준 시각을 포함한다(Walk#status와 같은 기준).
	 */
	boolean existsOpenWalkActiveSince(Long petId, Instant activeSince);

}
