package com.pawmento.map.adapter.out.geocoding;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawmento.map.application.port.out.LoadAddressPort;

/**
 * 네이버 Reverse Geocoding으로 좌표를 주소로 바꾼다.
 * 키가 없거나, 호출이 실패하거나, 시간이 오래 걸리면 빈 값을 돌려준다. 예외를 밖으로 던지지 않는다.
 */
@Component
class NaverAddressAdapter implements LoadAddressPort {

	private static final Logger log = LoggerFactory.getLogger(NaverAddressAdapter.class);

	private static final String PATH = "/map-reversegeocode/v2/gc";

	// 도로명 주소를 먼저 찾고, 없으면 지번 주소를 쓴다
	private static final String ORDERS = "roadaddr,addr";

	private final NaverMapProperties properties;
	private final RestClient restClient;

	@Autowired
	NaverAddressAdapter(NaverMapProperties properties) {
		this(properties, builderWithTimeouts(properties));
	}

	// 테스트에서 가짜 서버를 연결한 builder를 넣을 수 있게 한다
	NaverAddressAdapter(NaverMapProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
	}

	private static RestClient.Builder builderWithTimeouts(NaverMapProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().requestFactory(requestFactory);
	}

	@Override
	public Optional<String> loadByCoordinates(double latitude, double longitude) {
		if (!properties.hasCredentials()) {
			log.warn("네이버 지도 키가 설정되지 않아 주소를 조회하지 않습니다.");
			return Optional.empty();
		}
		try {
			// 네이버는 좌표를 경도,위도 순서로 받는다. 문서 예시처럼 쉼표를 그대로 보낸다
			String coords = plain(longitude) + "," + plain(latitude);
			ReverseGeocodeResponse response = restClient.get()
				.uri(uriBuilder -> uriBuilder.path(PATH)
					.queryParam("coords", coords)
					.queryParam("orders", ORDERS)
					.queryParam("output", "json")
					.build())
				.header("X-NCP-APIGW-API-KEY-ID", properties.clientId())
				.header("X-NCP-APIGW-API-KEY", properties.clientSecret())
				.retrieve()
				.body(ReverseGeocodeResponse.class);
			return Optional.ofNullable(response).flatMap(NaverAddressAdapter::toAddress);
		}
		catch (RuntimeException e) {
			// 요청 헤더(키)는 로그에 남기지 않는다
			log.warn("주소 조회에 실패했습니다: {}", e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	private static String plain(double value) {
		return BigDecimal.valueOf(value).toPlainString();
	}

	private static Optional<String> toAddress(ReverseGeocodeResponse response) {
		if (response.status() == null || response.status().code() != 0 || response.results() == null) {
			return Optional.empty();
		}
		return findResult(response, "roadaddr").or(() -> findResult(response, "addr"))
			.map(NaverAddressAdapter::compose)
			.filter(address -> !address.isBlank());
	}

	private static Optional<Result> findResult(ReverseGeocodeResponse response, String name) {
		return response.results().stream().filter(result -> name.equals(result.name())).findFirst();
	}

	// 시·도, 시·군·구, (도로명 또는 동·리), 번호 순으로 이어 붙인다
	private static String compose(Result result) {
		List<String> parts = new ArrayList<>();
		if (result.region() != null) {
			add(parts, result.region().area1());
			add(parts, result.region().area2());
			if ("roadaddr".equals(result.name())) {
				addText(parts, result.land() == null ? null : result.land().name());
			}
			else {
				add(parts, result.region().area3());
				add(parts, result.region().area4());
			}
		}
		if (result.land() != null) {
			addText(parts, number(result.land()));
		}
		return String.join(" ", parts);
	}

	private static String number(Land land) {
		if (land.number1() == null || land.number1().isBlank()) {
			return null;
		}
		if (land.number2() == null || land.number2().isBlank()) {
			return land.number1();
		}
		return land.number1() + "-" + land.number2();
	}

	private static void add(List<String> parts, Area area) {
		if (area != null) {
			addText(parts, area.name());
		}
	}

	private static void addText(List<String> parts, String text) {
		if (text != null && !text.isBlank()) {
			parts.add(text);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReverseGeocodeResponse(Status status, List<Result> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Status(int code) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Result(String name, Region region, Land land) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Region(Area area1, Area area2, Area area3, Area area4) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Area(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Land(String name, String number1, String number2) {
	}

}
