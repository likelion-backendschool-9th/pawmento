package com.pawmento.map.domain;

/**
 * 행정안전부 인허가 데이터(병원·미용)의 행 하나에서 우리가 쓰는 값. 모두 원본의 문자열 그대로다.
 * 값이 null이면 응답에 그 항목이 없었다는 뜻이고, 빈 문자열은 항목은 있지만 값이 비어 있다는 뜻이다.
 *
 * @param sourceId 관리번호(MNG_NO)
 * @param businessStatus 영업 상태(SALS_STTS_NM). 영업/정상, 폐업 등
 * @param name 사업장 이름(BPLC_NM)
 * @param roadAddress 도로명 주소(ROAD_NM_ADDR)
 * @param lotAddress 지번 주소(LOTNO_ADDR)
 * @param phone 전화번호(TELNO)
 * @param x TM 좌표 X(CRD_INFO_X)
 * @param y TM 좌표 Y(CRD_INFO_Y)
 * @param updatedAt 원본 갱신 시각(DAT_UPDT_PNT). 한국 시각 "yyyy-MM-dd HH:mm:ss"
 */
public record LocalDataRecord(String sourceId, String businessStatus, String name, String roadAddress,
		String lotAddress, String phone, String x, String y, String updatedAt) {

	/**
	 * 가져오기에 꼭 필요한 항목(관리번호, 상태, 이름, 좌표 두 항목)이 응답에 있는지. 하나라도 없으면 원본의 형식이 바뀌었을 수
	 * 있어서, 일부만 저장하지 않고 가져오기 전체를 실패시킨다(#172-S5). 값이 비어 있는 것(빈 좌표)은 있는 것으로 센다.
	 */
	public boolean isComplete() {
		return sourceId != null && businessStatus != null && name != null && x != null && y != null;
	}

}
