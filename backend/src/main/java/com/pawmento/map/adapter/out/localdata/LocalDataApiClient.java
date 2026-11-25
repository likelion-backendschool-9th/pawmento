package com.pawmento.map.adapter.out.localdata;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.port.out.LoadLocalDataPort;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;

import tools.jackson.databind.JsonNode;

/**
 * 행정안전부 인허가 조회서비스(병원 animal_hospitals, 미용 pet_grooming)를 페이지 끝까지 받는다. (#172-S5, S20)
 * 호출과 다시 시도는 {@link PublicDataPageFetcher}가 한다. 그래도 받지 못하면 일부만 돌려주지 않고 가져오기 전체를 중단한다.
 */
@Component
class LocalDataApiClient implements LoadLocalDataPort {

	private final LocalDataProperties properties;

	private final PublicDataPageFetcher fetcher;

	@Autowired
	LocalDataApiClient(LocalDataProperties properties) {
		this(properties, PublicDataPageFetcher::sleep);
	}

	// 테스트에서 쉬는 시간을 기록만 하고 실제로 쉬지 않게 한다
	LocalDataApiClient(LocalDataProperties properties, Consumer<Duration> sleeper) {
		this.properties = properties;
		this.fetcher = new PublicDataPageFetcher(properties, sleeper);
	}

	@Override
	public LocalDataSnapshot load(PlaceCategory category) {
		String path = pathOf(category);
		fetcher.requireServiceKey();

		PublicDataPageFetcher.Page first = fetcher.fetchPage(path, 1, properties.pageSize(), "returnType");
		int pages = first.totalCount() == 0 ? 0 : (first.totalCount() + properties.pageSize() - 1) / properties.pageSize();
		List<LocalDataRecord> records = new ArrayList<>(records(first));
		int totalCountAtEnd = first.totalCount();
		for (int pageNo = 2; pageNo <= pages; pageNo++) {
			fetcher.pause();
			PublicDataPageFetcher.Page page = fetcher.fetchPage(path, pageNo, properties.pageSize(), "returnType");
			records.addAll(records(page));
			totalCountAtEnd = page.totalCount();
		}
		return new LocalDataSnapshot(first.totalCount(), totalCountAtEnd, records);
	}

	private static String pathOf(PlaceCategory category) {
		return switch (category) {
			case HOSPITAL -> "/1741000/animal_hospitals/info";
			case GROOMING -> "/1741000/pet_grooming/info";
			default -> throw new IllegalArgumentException("이 API로 받을 수 없는 종류입니다: " + category);
		};
	}

	private static List<LocalDataRecord> records(PublicDataPageFetcher.Page page) {
		return page.items().stream().map(LocalDataApiClient::record).toList();
	}

	private static LocalDataRecord record(JsonNode node) {
		return new LocalDataRecord(text(node, "MNG_NO"), text(node, "SALS_STTS_NM"), text(node, "BPLC_NM"),
				text(node, "ROAD_NM_ADDR"), text(node, "LOTNO_ADDR"), text(node, "TELNO"), text(node, "CRD_INFO_X"),
				text(node, "CRD_INFO_Y"), text(node, "DAT_UPDT_PNT"));
	}

	private static String text(JsonNode node, String name) {
		return PublicDataPageFetcher.text(node, name);
	}

}
