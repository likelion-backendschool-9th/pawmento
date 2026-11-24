package com.pawmento.map.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.map.application.port.in.GetPlaceSyncStatusQuery;
import com.pawmento.map.application.port.in.GetPlaceSyncStatusUseCase;
import com.pawmento.map.application.port.in.StartPlaceSyncCommand;
import com.pawmento.map.application.port.in.StartPlaceSyncUseCase;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

// 관리자 전용이다. 실제 경로는 /api/v1/admin/places/...이고 /api/v1/admin/** 은 ADMIN 역할만 들어온다
@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
class AdminPlaceController {

	private final StartPlaceSyncUseCase startPlaceSyncUseCase;

	private final GetPlaceSyncStatusUseCase getPlaceSyncStatusUseCase;

	// 가져오기를 시작만 하고 바로 202로 돌려준다. 몇 분 걸려서 끝까지 기다리면 nginx 시간 제한(60초)에 걸린다
	@PostMapping("/sync")
	@ResponseStatus(HttpStatus.ACCEPTED)
	PlaceSyncStatusResponse start(@LoginMember Long memberId, HttpServletRequest http) {
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "category");
		return PlaceSyncStatusResponse
			.from(startPlaceSyncUseCase.start(new StartPlaceSyncCommand(memberId, query.syncCategory("category"))));
	}

	// 마지막 가져오기의 진행·결과를 조회한다
	@GetMapping("/sync")
	PlaceSyncStatusResponse status(@LoginMember Long memberId, HttpServletRequest http) {
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "category");
		return PlaceSyncStatusResponse
			.from(getPlaceSyncStatusUseCase.get(new GetPlaceSyncStatusQuery(memberId, query.syncCategory("category"))));
	}

}
