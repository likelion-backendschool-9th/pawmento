package com.pawmento.map.application.port.out;

import java.util.List;

import com.pawmento.map.domain.ShelterRecord;

/**
 * 보호센터를 한 번 호출해 받은 결과. 이 API는 totalCount(868)가 실제 행 수(339)와 달라서 totalCount로 온전함을 판단하지 않는다.
 * 한 번에 충분히 크게 요청해서 받은 건수가 요청한 수보다 적으면 끝까지 받은 것으로 본다.
 *
 * @param requestedRows 한 번에 달라고 요청한 행 수
 * @param records 받은 행 전부
 */
public record ShelterSnapshot(int requestedRows, List<ShelterRecord> records) {
}
