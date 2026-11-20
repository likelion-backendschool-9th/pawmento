# 다이어리 위치 입력과 원본 저장 준비 (#32, #44)

승인된 [수동 위치 계획](../map/photo-location.md#수동-위치해제-구현-계획-44-32)의 diary 쪽 입력·저장 기반이다. 지도 반영 포트와 주소 조회 포트는 아직 연결하지 않았다. 지도 담당의 출처/revision 저장 작업(#117)과 별개로 구현하며 map 저장소를 diary에서 직접 호출하지 않는다.

## 현재 연결 범위

- 웹 요청의 location을 `DiaryLocationChange`로 변환한다. 생략은 KEEP, 명시는 NONE/MANUAL/AUTO로 구분한다. MANUAL의 좌표·정규화된 장소명은 `DiaryLocationIntent`로 코어 입력까지 전달하며 JSON 타입은 웹 계층에 남긴다.
- 좌표 누락·숫자 문자열·불리언·범위 초과와 서버 소유 필드 주입은 400이다. 이름은 null/공백을 비사용으로 정규화하고 strip 후 Java String.length 255 이내로 검사한다. 유효한 MANUAL/AUTO는 아직 기존 미지원 409를 유지한다.
- 새 일기는 위치 원본 NONE으로 저장한다. `diary_entries`의 location_mode, location_latitude, location_longitude, location_place_name을 일기 본문·날짜·version의 조건부 갱신에 함께 저장한다. 좌표는 double precision이고 소수 반올림 없이 0의 부호만 정규화한다.
- 기존 행의 location_mode NULL은 미이관 상태다. 명시적인 NONE과 구분하며 기존 map 값을 지우거나 임의로 NONE으로 백필하지 않는다. 원본이 이미 있는 행은 본문 수정 중에도 그 값을 보존한다.
- 스키마는 nullable 열을 반복 적용 가능하게 추가하고 값 조합·좌표 범위를 CHECK로 보호한다. 잘못 저장된 원본을 읽는 경우에는 입력 오류 400으로 재포장하지 않는다.

## 아직 활성화하지 않은 범위

MANUAL/NONE 위치만 PATCH, 응답 위치와 열람 권한, 지도 apply/정정/주소 조회, 원본 초기 백필, map revision 비교·삭제·전체 롤백 연결은 후속이다. 현재 API의 NONE 요청이 지도 핀 해제를 구현했다는 뜻이 아니며 새 지도 쓰기 API나 성공을 반환하는 임시 어댑터는 추가하지 않는다.

지도 입력 계약이 제공되면 짧은 사전 조회와 저장 트랜잭션을 분리하고, 원본 백필·권한·버전 재검사·부모→자산 잠금·지도 반영을 연결한다. 상세 정책과 미완료 시나리오는 상위 계획을 따른다.
