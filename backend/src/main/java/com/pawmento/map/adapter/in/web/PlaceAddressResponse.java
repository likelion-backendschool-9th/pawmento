package com.pawmento.map.adapter.in.web;

import java.util.Optional;

// 찾은 주소. 찾지 못했거나 가려지지 않은 주소이면 address가 null이다
record PlaceAddressResponse(String address) {

	static PlaceAddressResponse from(Optional<String> address) {
		return new PlaceAddressResponse(address.orElse(null));
	}

}
