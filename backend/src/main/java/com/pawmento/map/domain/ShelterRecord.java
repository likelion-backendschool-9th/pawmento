package com.pawmento.map.domain;

/**
 * 농림축산식품부 동물보호센터 조회서비스의 행 하나에서 우리가 쓰는 값. 모두 원본의 문자열 그대로다.
 * 센터마다 있는 항목이 달라서 응답에 항목이 없으면 null이고, 있는데 값이 비어 있으면 빈 문자열이다.
 *
 * @param careRegNo 동물보호센터 등록번호(careRegNo). 센터의 고유 키
 * @param name 센터 이름(careNm)
 * @param address 도로명 주소(careAddr). 지번 주소(jibunAddr)는 온전하지 않은 값이 있어 쓰지 않는다
 * @param phone 전화번호(careTel)
 * @param latitude 위도(lat). 이미 WGS84라 변환하지 않는다
 * @param longitude 경도(lng)
 * @param openTime 평일 운영 시작 시각(weekOprStime). "09:00"
 * @param closeTime 평일 운영 종료 시각(weekOprEtime). "18:00", 24시간이면 "24:00"
 * @param closeDay 휴무일(closeDay). "토요일+일요일"처럼 요일 글자일 때가 많고 "0" 같은 숫자 값도 있다
 * @param dataStdDt 데이터 기준일(dataStdDt). "yyyy-MM-dd"
 */
public record ShelterRecord(String careRegNo, String name, String address, String phone, String latitude,
		String longitude, String openTime, String closeTime, String closeDay, String dataStdDt) {

	/**
	 * 가져오기에 꼭 필요한 항목(등록번호, 이름)이 값까지 있는지. 센터를 구분하고 보여 주는 데 꼭 필요한 값이라, 빠진 행이 있으면
	 * 원본의 형식이 바뀌었을 수 있어서 일부만 저장하지 않고 가져오기 전체를 실패시킨다(#172-S9). 위도·경도는 센터마다 항목이
	 * 아예 없거나 비어 있어서(실제 데이터에서 확인), 없으면 장소로 만들 때 좌표 없음으로 제외한다.
	 */
	public boolean isComplete() {
		return careRegNo != null && !careRegNo.isBlank() && name != null && !name.isBlank();
	}

}
