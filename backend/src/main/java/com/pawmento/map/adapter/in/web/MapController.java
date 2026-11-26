package com.pawmento.map.adapter.in.web;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.map.adapter.in.web.exception.InvalidMapRequestException;
import com.pawmento.map.application.port.in.AddWalkPointsUseCase;
import com.pawmento.map.application.port.in.EndWalkCommand;
import com.pawmento.map.application.port.in.EndWalkUseCase;
import com.pawmento.map.application.port.in.GetCurrentWalkQuery;
import com.pawmento.map.application.port.in.ResolvePlaceAddressUseCase;
import com.pawmento.map.application.port.in.SearchPlacesQuery;
import com.pawmento.map.application.port.in.SearchPlacesUseCase;
import com.pawmento.map.application.port.in.GetCurrentWalkUseCase;
import com.pawmento.map.application.port.in.ListMapPinsQuery;
import com.pawmento.map.application.port.in.ListMapPinsUseCase;
import com.pawmento.map.application.port.in.ListPlacesQuery;
import com.pawmento.map.application.port.in.ListPlacesUseCase;
import com.pawmento.map.application.port.in.ListWalksQuery;
import com.pawmento.map.application.port.in.ListWalksUseCase;
import com.pawmento.map.application.port.in.StartWalkCommand;
import com.pawmento.map.application.port.in.StartWalkUseCase;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 실제 경로는 /api/v1/maps/...다. /api/v1은 WebConfig가 붙인다
@RestController
@RequestMapping("/maps")
@RequiredArgsConstructor
class MapController {

	private final ListMapPinsUseCase listMapPinsUseCase;
	private final ListPlacesUseCase listPlacesUseCase;
	private final ResolvePlaceAddressUseCase resolvePlaceAddressUseCase;
	private final SearchPlacesUseCase searchPlacesUseCase;
	private final StartWalkUseCase startWalkUseCase;
	private final AddWalkPointsUseCase addWalkPointsUseCase;
	private final EndWalkUseCase endWalkUseCase;
	private final ListWalksUseCase listWalksUseCase;
	private final GetCurrentWalkUseCase getCurrentWalkUseCase;

	@GetMapping("/pins")
	MapPinsResponse listPins(@LoginMember Long memberId, HttpServletRequest http) {
		// 쿼리 문자열만 읽는다. 모델 바인딩은 URL에 없는 값을 같은 이름의 헤더로 채우고 중복된 키는 첫 값만 쓴다
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "petId", "minLatitude",
				"minLongitude", "maxLatitude", "maxLongitude");
		ListMapPinsQuery pinsQuery = new ListMapPinsQuery(memberId, query.positiveLong("petId"),
				query.latitude("minLatitude"), query.longitude("minLongitude"), query.latitude("maxLatitude"),
				query.longitude("maxLongitude"));
		return MapPinsResponse.from(listMapPinsUseCase.list(pinsQuery));
	}

	// 병원·미용·보호센터를 영역 안에서 찾는다. 장소는 반려동물과 상관없어 petId를 받지 않는다
	@GetMapping("/places")
	PlacesResponse listPlaces(@LoginMember Long memberId, HttpServletRequest http) {
		// 쿼리 문자열만 읽는다(핀 조회와 같은 이유). 로그인은 필요하지만 장소는 모든 회원이 같이 본다
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "minLatitude", "minLongitude",
				"maxLatitude", "maxLongitude", "categories");
		return PlacesResponse.from(listPlacesUseCase.list(new ListPlacesQuery(query.latitude("minLatitude"),
				query.longitude("minLongitude"), query.latitude("maxLatitude"), query.longitude("maxLongitude"),
				query.placeCategories("categories"))));
	}

	// 이름이나 주소로 병원·미용·보호센터를 찾는다(#195). 지도 영역과 상관없이 전체에서 찾고, 지도 중심(latitude·longitude)을 함께 보내면 가까운 순이다.
	// 검색어는 쿼리 문자열로 받는다. 애플리케이션의 로그와 오류 응답에는 남기지 않는다
	@GetMapping("/places/search")
	PlaceSearchResponse searchPlaces(@LoginMember Long memberId, HttpServletRequest http) {
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "q", "categories", "latitude",
				"longitude");
		BigDecimal latitude = query.optionalLatitude("latitude");
		BigDecimal longitude = query.optionalLongitude("longitude");
		if ((latitude == null) != (longitude == null)) {
			throw InvalidMapRequestException.centerNeedsBoth();
		}
		return PlaceSearchResponse.from(searchPlacesUseCase.search(
				new SearchPlacesQuery(query.text("q"), query.placeCategories("categories"), latitude, longitude)));
	}

	// 주소가 가려진 장소의 주소를 좌표로 찾는다(#239). 장소 id만 받고 서버가 좌표를 읽는다. 찾지 못하면 address가 null이다
	@GetMapping("/places/{placeId}/address")
	PlaceAddressResponse resolvePlaceAddress(@LoginMember Long memberId, @PathVariable Long placeId) {
		return PlaceAddressResponse.from(resolvePlaceAddressUseCase.resolve(placeId));
	}

	@GetMapping("/walks")
	WalksResponse listWalks(@LoginMember Long memberId, HttpServletRequest http) {
		// 쿼리 문자열만 읽는다. 모델 바인딩은 URL에 없는 값을 헤더로 채우고 날짜의 offset 접미사도 받는다
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "petId", "date");
		return WalksResponse
			.from(listWalksUseCase.list(new ListWalksQuery(memberId, query.positiveLong("petId"), query.date("date"))));
	}

	@GetMapping("/walks/current")
	ResponseEntity<CurrentWalkResponse> currentWalk(@LoginMember Long memberId, HttpServletRequest http) {
		// 쿼리 문자열만 읽는다. 모델 바인딩은 URL에 없는 값을 같은 이름의 헤더로 채우고 중복된 키는 첫 값만 쓴다
		MapQueryParameters query = MapQueryParameters.parse(http.getQueryString(), "petId");
		// 진행 중인 산책이 없으면 204다. 읽지 못한 것(오류)과 구분해서 화면이 시작 화면을 열 수 있게 한다
		return getCurrentWalkUseCase.get(new GetCurrentWalkQuery(memberId, query.positiveLong("petId")))
			.map(CurrentWalkResponse::from)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
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
