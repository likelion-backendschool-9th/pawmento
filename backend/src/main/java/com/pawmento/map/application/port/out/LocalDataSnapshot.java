package com.pawmento.map.application.port.out;

import java.util.List;

import com.pawmento.map.domain.LocalDataRecord;

/**
 * 공공데이터를 처음부터 끝까지 받은 결과. 믿고 반영할 수 있는지는 서비스가 판단한다.
 *
 * @param totalCountAtStart 첫 페이지 응답의 totalCount
 * @param totalCountAtEnd 마지막 페이지 응답의 totalCount. 수집하는 동안 바뀌었는지 알아내는 데 쓴다
 * @param records 받은 행 전부. 정렬이 보장되지 않아 페이지가 겹치면 같은 행이 둘 이상 들어 있을 수 있다
 */
public record LocalDataSnapshot(int totalCountAtStart, int totalCountAtEnd, List<LocalDataRecord> records) {
}
