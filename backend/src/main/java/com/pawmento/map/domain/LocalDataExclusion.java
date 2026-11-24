package com.pawmento.map.domain;

// 인허가 행을 장소로 저장하지 않은 이유. 가져오기 결과의 "제외 건수"를 이유별로 센다
public enum LocalDataExclusion {

	// 영업/정상이 아니다(폐업, 휴업, 취소 등)
	CLOSED,

	// 좌표가 비어 있다
	NO_COORDINATES,

	// 좌표가 숫자가 아니거나 변환한 위도·경도가 대한민국 범위 밖이다. 좌표를 임의로 고치지 않는다
	OUT_OF_RANGE

}
