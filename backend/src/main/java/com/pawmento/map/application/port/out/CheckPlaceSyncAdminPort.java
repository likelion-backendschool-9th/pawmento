package com.pawmento.map.application.port.out;

public interface CheckPlaceSyncAdminPort {

	/**
	 * 회원이 지금 관리자인지 저장소의 현재 역할로 확인한다. 토큰에 적힌 역할만 믿으면 역할이 바뀐 뒤에도 쓰던 토큰으로 돌릴 수 있다.
	 *
	 * @throws com.pawmento.map.application.exception.PlaceSyncAdminDeniedException 관리자가 아니거나 회원이 없을 때
	 */
	void requireAdmin(Long memberId);

}
