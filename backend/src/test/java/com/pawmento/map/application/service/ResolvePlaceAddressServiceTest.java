package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.PlaceNotFoundException;
import com.pawmento.map.application.port.out.LoadPlaceByIdPort;
import com.pawmento.map.application.port.out.LoadRoadAddressPort;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

// 시나리오는 docs/map/place-address.md(#239-S1, S2, S3, S6)에 있다. 외부 서비스는 가짜 포트로 대신한다
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
	@DisplayName("#239-S1 가려진 주소는 장소의 좌표로 도로명 주소를 찾고 건물 이름을 붙여 돌려준다")
	void resolvesMaskedAddressByTheStoredCoordinates() {
		when(loadPlaceByIdPort.loadById(7L)).thenReturn(Optional.of(place(7L, MASKED)));
		when(loadRoadAddressPort.loadRoadAddressByCoordinates(37.5445, 127.0374))
			.thenReturn(Optional.of("서울특별시 성동구 서울숲2길 32-14"));

		assertThat(service.resolve(7L)).contains("서울특별시 성동구 서울숲2길 32-14 (갤러리아 포레)");
	}

	@Test
	@DisplayName("#239-S2 가려지지 않은 주소와 비어 있는 주소는 외부 서비스를 부르지 않고 빈 값이다")
	void doesNotCallWhenAddressIsNotMasked() {
		when(loadPlaceByIdPort.loadById(1L)).thenReturn(Optional.of(place(1L, "서울 종로구 대학로 1")));
		when(loadPlaceByIdPort.loadById(2L)).thenReturn(Optional.of(place(2L, null)));

		assertThat(service.resolve(1L)).isEmpty();
		assertThat(service.resolve(2L)).isEmpty();

		verify(loadRoadAddressPort, never()).loadRoadAddressByCoordinates(anyDouble(), anyDouble());
	}

	@Test
	@DisplayName("#239-S3 외부 서비스가 주소를 못 찾으면 빈 값이고 오류로 끝나지 않는다")
	void emptyWhenTheExternalServiceFindsNothing() {
		when(loadPlaceByIdPort.loadById(7L)).thenReturn(Optional.of(place(7L, MASKED)));
		when(loadRoadAddressPort.loadRoadAddressByCoordinates(37.5445, 127.0374)).thenReturn(Optional.empty());

		assertThat(service.resolve(7L)).isEmpty();
	}

	@Test
	@DisplayName("#239-S3 외부 서비스가 빈 글자를 주면 빈 값이다")
	void emptyWhenTheExternalServiceReturnsBlank() {
		when(loadPlaceByIdPort.loadById(7L)).thenReturn(Optional.of(place(7L, MASKED)));
		when(loadRoadAddressPort.loadRoadAddressByCoordinates(37.5445, 127.0374)).thenReturn(Optional.of("   "));

		assertThat(service.resolve(7L)).isEmpty();
	}

	@Test
	@DisplayName("#239-S6 없는 장소이면 장소를 찾을 수 없고 외부 서비스는 불리지 않는다")
	void notFoundForUnknownPlace() {
		when(loadPlaceByIdPort.loadById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.resolve(99L)).isInstanceOf(PlaceNotFoundException.class);

		verify(loadRoadAddressPort, never()).loadRoadAddressByCoordinates(anyDouble(), anyDouble());
	}

	@Test
	@DisplayName("#239-S6 id가 없거나 양의 정수가 아니면 저장소를 읽지 않고 장소를 찾을 수 없다")
	void notFoundForInvalidId() {
		assertThatThrownBy(() -> service.resolve(null)).isInstanceOf(PlaceNotFoundException.class);
		assertThatThrownBy(() -> service.resolve(0L)).isInstanceOf(PlaceNotFoundException.class);
		assertThatThrownBy(() -> service.resolve(-3L)).isInstanceOf(PlaceNotFoundException.class);

		verify(loadPlaceByIdPort, never()).loadById(any());
	}

}
