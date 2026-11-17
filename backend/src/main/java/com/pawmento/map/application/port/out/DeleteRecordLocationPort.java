package com.pawmento.map.application.port.out;

public interface DeleteRecordLocationPort {

	// 그 기록의 위치를 지운다. 위치가 없어도 오류가 아니다
	void deleteByRecordId(Long recordId);

}
