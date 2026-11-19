package com.pawmento.map.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.map.application.port.in.AddWalkPointsUseCase;
import com.pawmento.map.application.port.in.EndWalkCommand;
import com.pawmento.map.application.port.in.EndWalkUseCase;
import com.pawmento.map.application.port.in.ListMapPinsQuery;
import com.pawmento.map.application.port.in.ListMapPinsUseCase;
import com.pawmento.map.application.port.in.StartWalkCommand;
import com.pawmento.map.application.port.in.StartWalkUseCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 실제 경로는 /api/v1/maps/...다. /api/v1은 WebConfig가 붙인다
@RestController
@RequestMapping("/maps")
@RequiredArgsConstructor
class MapController {

	private final ListMapPinsUseCase listMapPinsUseCase;
	private final StartWalkUseCase startWalkUseCase;
	private final AddWalkPointsUseCase addWalkPointsUseCase;
	private final EndWalkUseCase endWalkUseCase;

	@GetMapping("/pins")
	MapPinsResponse listPins(@LoginMember Long memberId, @Valid ListMapPinsRequest request) {
		ListMapPinsQuery query = new ListMapPinsQuery(memberId, request.petId(), request.minLatitude(), request.minLongitude(),
				request.maxLatitude(), request.maxLongitude());
		return MapPinsResponse.from(listMapPinsUseCase.list(query));
	}


	@PostMapping("/walks")
	@ResponseStatus(HttpStatus.CREATED)
	StartWalkResponse startWalk(@LoginMember Long memberId, @Valid @RequestBody StartWalkRequest request) {
		return StartWalkResponse.from(startWalkUseCase.start(new StartWalkCommand(memberId, request.petIdValue())));
	}

	@PostMapping("/walks/{walkId}/points")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void addWalkPoints(@LoginMember Long memberId, @PathVariable Long walkId,
			@Valid @RequestBody AddWalkPointsRequest request) {
		addWalkPointsUseCase.add(request.toCommand(memberId, walkId));
	}

	@PostMapping("/walks/{walkId}/end")
	EndWalkResponse endWalk(@LoginMember Long memberId, @PathVariable Long walkId) {
		return EndWalkResponse.from(endWalkUseCase.end(new EndWalkCommand(memberId, walkId)));
	}

}
