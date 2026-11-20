package com.pawmento.map.domain;

/**
 * 위치가 어디서 왔는지. 지금은 사용자가 직접 고른 위치(MANUAL)만 있다.
 * 사진의 GPS에서 고른 위치(AUTO)는 위치 동의 정책이 정해진 뒤에 더한다.
 */
public enum LocationSource {

	MANUAL

}
