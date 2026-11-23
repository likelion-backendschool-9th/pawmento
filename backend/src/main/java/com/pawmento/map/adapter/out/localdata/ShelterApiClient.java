package com.pawmento.map.adapter.out.localdata;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.port.out.LoadShelterDataPort;
import com.pawmento.map.application.port.out.ShelterSnapshot;
import com.pawmento.map.domain.ShelterRecord;

import tools.jackson.databind.JsonNode;

/**
 * 농림축산식품부 동물보호센터 조회서비스(shelterInfo_v2)를 받는다. 이 API는 totalCount가 868인데 실제 센터는 339곳이고
 * numOfRows를 500이나 1000으로 해도 339건이 와서, 한 번에 500건을 요청해 받은 건수가 그보다 적으면 끝까지 받은 것으로 본다.
 * 호출과 다시 시도는 {@link PublicDataPageFetcher}가 한다. (#172-S8, S9)
 */
@Component
class ShelterApiClient implements LoadShelterDataPort {

	static final String PATH = "/1543061/animalShelterSrvc_v2/shelterInfo_v2";

	static final int REQUESTED_ROWS = 500;

	private final PublicDataPageFetcher fetcher;

	@Autowired
	ShelterApiClient(LocalDataProperties properties) {
		this(properties, LocalDataApiClient::sleep);
	}

	// 테스트에서 쉬는 시간을 기록만 하고 실제로 쉬지 않게 한다
	ShelterApiClient(LocalDataProperties properties, Consumer<Duration> sleeper) {
		this.fetcher = new PublicDataPageFetcher(properties, sleeper);
	}

	@Override
	public ShelterSnapshot load() {
		fetcher.requireServiceKey();
		PublicDataPageFetcher.Page page = fetcher.fetchPage(PATH, 1, REQUESTED_ROWS, "_type");
		List<ShelterRecord> records = page.items().stream().map(ShelterApiClient::record).toList();
		return new ShelterSnapshot(REQUESTED_ROWS, records);
	}

	private static ShelterRecord record(JsonNode node) {
		return new ShelterRecord(text(node, "careRegNo"), text(node, "careNm"), text(node, "careAddr"),
				text(node, "careTel"), text(node, "lat"), text(node, "lng"), text(node, "weekOprStime"),
				text(node, "weekOprEtime"), text(node, "closeDay"), text(node, "dataStdDt"));
	}

	private static String text(JsonNode node, String name) {
		return PublicDataPageFetcher.text(node, name);
	}

}
