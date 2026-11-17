package com.pawmento.map.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.map.application.port.in.ListMapPinsQuery;
import com.pawmento.map.application.port.in.ListMapPinsUseCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 실제 경로는 /api/v1/maps/pins다. /api/v1은 WebConfig가 붙인다
@RestController
@RequestMapping("/maps")
@RequiredArgsConstructor
class MapController {

	private final ListMapPinsUseCase listMapPinsUseCase;

	@GetMapping("/pins")
	MapPinsResponse listPins(@LoginMember Long memberId, @Valid ListMapPinsRequest request) {
		ListMapPinsQuery query = new ListMapPinsQuery(memberId, request.petId(), request.minLatitude(), request.minLongitude(),
				request.maxLatitude(), request.maxLongitude());
		return MapPinsResponse.from(listMapPinsUseCase.list(query));
	}

}
