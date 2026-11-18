package com.pawmento.map.domain;

/**
 * 산책의 상태. 저장하지 않고 {@link Walk#status}가 시각을 보고 계산한다.
 */
public enum WalkStatus {

	// 아직 끝내지 않았고 최근에 좌표를 받았다
	IN_PROGRESS,

	// 보호자가 끝냈다
	ENDED,

	// 끝내지 않았는데 마지막 좌표를 받은 지 오래됐다. 앱이 꺼진 것으로 본다
	INTERRUPTED

}
