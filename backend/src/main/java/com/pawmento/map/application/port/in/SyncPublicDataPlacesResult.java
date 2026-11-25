package com.pawmento.map.application.port.in;

/**
 * 가져오기 결과 건수.
 *
 * @param read 읽은 행 수(겹친 페이지를 합친 고유 관리번호 수)
 * @param saved 장소로 저장할 행 수
 * @param excluded 제외한 행 수(closed + noCoordinates + outOfRange)
 * @param closed 영업/정상이 아니어서 제외한 행
 * @param noCoordinates 좌표가 비어 있어 제외한 행
 * @param outOfRange 좌표가 숫자가 아니거나 한국 범위 밖이라 제외한 행
 * @param inserted 새로 추가한 장소
 * @param updated 값이 바뀌어 갱신한 장소
 * @param deleted 이번 목록에 없어서 지운 장소
 */
public record SyncPublicDataPlacesResult(String category, int read, int saved, int excluded, int closed,
		int noCoordinates, int outOfRange, int inserted, int updated, int deleted) {
}
