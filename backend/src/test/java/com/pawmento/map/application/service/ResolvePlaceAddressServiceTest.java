package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.port.out.LoadPlaceByIdPort;
import com.pawmento.map.application.port.out.LoadRoadAddressPort;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

// 시나리오는 docs/map/place-address.md(#239-S1, S2, S3, S6)에 있다. 주소 찾기·미호출·못 찾음·없는 장소는 PlaceAddressScenarioTest가 확인한다.
// 여기에는 외부 서비스가 빈 글자를 주는 경우만 남긴다
class ResolvePlaceAddressServiceTest {

	private static final String MASKED = "서울특별시 성동구 서울숲*길 **-**, ***동 *층 ***호 (성수동*가, 갤러리아 포레)";

	private final LoadPlaceByIdPort loadPlaceByIdPort = mock(LoadPlaceByIdPort.class);

	private final LoadRoadAddressPort loadRoadAddressPort = mock(LoadRoadAddressPort.class);

	private final ResolvePlaceAddressService service = new ResolvePlaceAddressService(loadPlaceByIdPort,
			loadRoadAddressPort);

	private static Place place(long id, String address) {
		return new Place(id, PlaceCategory.HOSPITAL, "갤러리아 동물병원", address, null, 37.5445, 127.0374, false, null, null,
				null);
	}

	@Test
	@DisplayName("#239-S3 외부 서비스가 빈 글자를 주면 빈 값이다")
	void emptyWhenTheExternalServiceReturnsBlank() {
		when(loadPlaceByIdPort.loadById(7L)).thenReturn(Optional.of(place(7L, MASKED)));
		when(loadRoadAddressPort.loadRoadAddressByCoordinates(37.5445, 127.0374)).thenReturn(Optional.of("   "));

		assertThat(service.resolve(7L)).isEmpty();
	}

}
