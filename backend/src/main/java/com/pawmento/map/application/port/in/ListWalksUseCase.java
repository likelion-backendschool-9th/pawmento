package com.pawmento.map.application.port.in;

public interface ListWalksUseCase {

	/**
	 * 회원이 반려동물과 한 산책 중 그 날(Asia/Seoul)에 시작한 것을 경로와 함께 돌려준다.
	 * 만든 회원 본인의 산책만 나오고, 다른 회원의 반려동물을 조회하면 403이 아니라 빈 목록이다(핀 조회와 같다).
	 */
	ListWalksResult list(ListWalksQuery query);

}
