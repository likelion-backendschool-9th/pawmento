package com.pawmento.map.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.exception.PlaceNotFoundException;
import com.pawmento.map.application.port.in.ResolvePlaceAddressUseCase;
import com.pawmento.map.application.port.out.LoadPlaceByIdPort;
import com.pawmento.map.application.port.out.LoadRoadAddressPort;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceAddress;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ResolvePlaceAddressService implements ResolvePlaceAddressUseCase {

	private final LoadPlaceByIdPort loadPlaceByIdPort;

	private final LoadRoadAddressPort loadRoadAddressPort;

	/**
	 * 장소의 좌표는 서버가 읽는다. 호출한 쪽이 좌표를 보내지 않으므로 아무 좌표나 조회하는 데 쓰이지 않는다(#239-S6).
	 * 주소가 가려지지 않은 장소는 외부 서비스를 부르지 않는다(#239-S2). 결과는 저장하지 않는다(#239-S7).
	 * 외부 호출이 느릴 수 있어 DB 트랜잭션 안에서 부르지 않는다.
	 */
	@Override
	@Transactional(propagation = Propagation.NEVER)
	public Optional<String> resolve(Long placeId) {
		Place place = placeId == null || placeId <= 0 ? null : loadPlaceByIdPort.loadById(placeId).orElse(null);
		if (place == null) {
			throw new PlaceNotFoundException();
		}
		if (!PlaceAddress.isMasked(place.address())) {
			return Optional.empty();
		}
		return loadRoadAddressPort.loadRoadAddressByCoordinates(place.latitude(), place.longitude())
			.map(String::strip)
			.filter(road -> !road.isEmpty())
			.map(road -> PlaceAddress.withBuildingName(road, place.address()));
	}

}
