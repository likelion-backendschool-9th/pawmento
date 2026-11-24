package com.pawmento.map.application.port.in;

/**
 * 공공데이터에서 장소를 가져오는 요청.
 *
 * @param requestedBy 요청한 관리자 회원. 자동 실행처럼 시스템이 요청하면 null이고, 있으면 현재 관리자인지 확인한다
 * @param category 가져올 종류의 이름. HOSPITAL 또는 GROOMING(보호센터는 다른 API라 따로 가져온다)
 */
public record SyncLocalDataPlacesCommand(Long requestedBy, String category) {

	/** 시스템(자동 실행)이 하는 요청. */
	public SyncLocalDataPlacesCommand(String category) {
		this(null, category);
	}

}
