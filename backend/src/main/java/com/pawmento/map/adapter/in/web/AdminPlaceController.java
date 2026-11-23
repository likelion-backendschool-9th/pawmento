package com.pawmento.map.adapter.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesUseCase;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

// 관리자 전용이다. 실제 경로는 /api/v1/admin/places/...이고 /api/v1/admin/** 은 ADMIN 역할만 들어온다
@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
class AdminPlaceController {

	private final SyncLocalDataPlacesUseCase syncLocalDataPlacesUseCase;

	// 공공데이터를 전부 받아 한 종류를 통째로 맞춘다. 끝날 때까지(보통 1~몇 분, 정부 서버가 혼잡하면 더 길게) 응답을 기다린다
	@PostMapping("/sync")
	SyncPlacesResponse sync(@LoginMember Long memberId, HttpServletRequest http) {
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "category");
		return SyncPlacesResponse
			.from(syncLocalDataPlacesUseCase.sync(new SyncLocalDataPlacesCommand(memberId, query.syncCategory("category"))));
	}

}
