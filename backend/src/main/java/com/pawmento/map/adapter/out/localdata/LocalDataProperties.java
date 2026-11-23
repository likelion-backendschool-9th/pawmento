package com.pawmento.map.adapter.out.localdata;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 공공데이터포털(행정안전부 인허가 데이터) 호출 설정 (application.yml의 pawmento.map.local-data).
 * 정부 서버는 시간대에 따라 호출의 상당수가 시간 초과·504로 실패해서(2026-11-23 실측: 호출 4번 중 1번, 혼잡할 때는 절반 이상),
 * 페이지마다 여러 번 다시 시도하도록 값을 넉넉히 잡았다. 값은 실제로 돌려 보며 조정한다.
 *
 * @param serviceKey 공공데이터포털 일반 인증키. 환경 변수 PUBLIC_DATA_SERVICE_KEY로만 받고 저장소에 두지 않는다.
 *            Encoding 키(%가 있음)와 Decoding 키 모두 쓸 수 있다
 * @param baseUrl 호출 주소
 * @param pageSize 한 번에 받는 건수. 이 API는 최대 100건만 준다
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간. 성공하면 평균 1.3초, 가장 느린 것이 16초였다
 * @param maxAttempts 한 페이지를 받으려고 시도하는 최대 횟수(처음 시도 포함)
 * @param backoff 다시 시도 전에 쉬는 시간의 시작값. 시도할 때마다 두 배로 늘린다
 * @param maxBackoff 다시 시도 전에 쉬는 시간의 최대값
 * @param pageInterval 페이지와 페이지 사이에 쉬는 시간. 하루 호출 한도(10,000건)와 서버 부담을 위해 둔다
 */
@ConfigurationProperties("pawmento.map.local-data")
public record LocalDataProperties(
		@DefaultValue("") String serviceKey,
		@DefaultValue("https://apis.data.go.kr") String baseUrl,
		@DefaultValue("100") int pageSize,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("20s") Duration readTimeout,
		@DefaultValue("8") int maxAttempts,
		@DefaultValue("1s") Duration backoff,
		@DefaultValue("30s") Duration maxBackoff,
		@DefaultValue("500ms") Duration pageInterval) {

	boolean hasServiceKey() {
		return !serviceKey.isBlank();
	}

}
