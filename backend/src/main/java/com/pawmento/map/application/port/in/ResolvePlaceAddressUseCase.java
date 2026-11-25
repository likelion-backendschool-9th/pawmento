package com.pawmento.map.application.port.in;

import java.util.Optional;

public interface ResolvePlaceAddressUseCase {

	/**
	 * 주소가 가려진 장소의 주소를 좌표로 찾는다. 찾지 못했거나 가려지지 않은 주소이면 빈 값이다.
	 *
	 * @throws com.pawmento.map.application.exception.PlaceNotFoundException 장소가 없을 때
	 */
	Optional<String> resolve(Long placeId);

}
