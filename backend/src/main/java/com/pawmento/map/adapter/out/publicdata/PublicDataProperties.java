package com.pawmento.map.adapter.out.publicdata;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 공공데이터포털(행정안전부 인허가 데이터와 농림축산식품부 보호센터 데이터) 호출 설정 (application.yml의 pawmento.map.public-data).
 * 두 데이터가 같은 인증키와 재시도 설정을 쓰므로 이 이름이 둘을 함께 가리킨다.
 * 정부 서버는 시간대에 따라 호출의 상당수가 시간 초과·504로 실패해서(2026-11-23 실측: 호출 4번 중 1번, 혼잡할 때는 절반 이상),
 * 페이지마다 여러 번 다시 시도하도록 값을 넉넉히 잡았다. 값은 실제로 돌려 보며 조정한다.
 *
 * @param serviceKey 공공데이터포털 일반 인증키. 환경 변수 PUBLIC_DATA_SERVICE_KEY로만 받고 저장소에 두지 않는다.
 *            Encoding 키(%가 있음)와 Decoding 키 모두 쓸 수 있다
 * @param baseUrl 호출 주소
 * @param pageSize 한 번에 받는 건수. 이 API는 최대 100건만 주므로 1~100이어야 한다
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간. 성공하면 평균 1.3초, 가장 느린 것이 16초였다
 * @param maxAttempts 한 페이지를 받으려고 시도하는 최대 횟수(처음 시도 포함)
 * @param backoff 다시 시도 전에 쉬는 시간의 시작값. 시도할 때마다 두 배로 늘린다
 * @param maxBackoff 다시 시도 전에 쉬는 시간의 최대값
 * @param pageInterval 페이지와 페이지 사이에 쉬는 시간. 하루 호출 한도(10,000건)와 서버 부담을 위해 둔다
 */
@ConfigurationProperties("pawmento.map.public-data")
public record PublicDataProperties(
		@DefaultValue("") String serviceKey,
		@DefaultValue("https://apis.data.go.kr") String baseUrl,
		@DefaultValue("100") int pageSize,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("20s") Duration readTimeout,
		@DefaultValue("8") int maxAttempts,
		@DefaultValue("1s") Duration backoff,
		@DefaultValue("30s") Duration maxBackoff,
		@DefaultValue("500ms") Duration pageInterval) {

	// 이 API는 한 번에 최대 100건만 준다
	static final int MAX_PAGE_SIZE = 100;

	// 잘못된 값은 앱이 시작할 때 거절한다. 0이나 100을 넘는 pageSize는 페이지 수 계산을 어긋나게 해서 원인을 알기 어려운 실패(0으로 나누기,
	// 매번 TOTAL_COUNT_MISMATCH)가 되고, maxAttempts=0은 한 번도 호출하지 않고 끝나기 때문이다
	public PublicDataProperties {
		if (serviceKey == null) {
			serviceKey = "";
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw invalid("page-size", "1~" + MAX_PAGE_SIZE, pageSize);
		}
		if (maxAttempts < 1) {
			throw invalid("max-attempts", "1 이상", maxAttempts);
		}
		requirePositive("connect-timeout", connectTimeout);
		requirePositive("read-timeout", readTimeout);
		requirePositive("backoff", backoff);
		requirePositive("max-backoff", maxBackoff);
		if (maxBackoff.compareTo(backoff) < 0) {
			throw invalid("max-backoff", "backoff 이상", maxBackoff);
		}
		if (pageInterval == null || pageInterval.isNegative()) {
			throw invalid("page-interval", "0 이상", pageInterval);
		}
	}

	private static void requirePositive(String name, Duration value) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw invalid(name, "0보다 큰 시간", value);
		}
	}

	private static IllegalArgumentException invalid(String name, String expected, Object actual) {
		return new IllegalArgumentException("pawmento.map.public-data." + name + "은(는) " + expected + "이어야 합니다: " + actual);
	}

	boolean hasServiceKey() {
		return !serviceKey.isBlank();
	}

}
