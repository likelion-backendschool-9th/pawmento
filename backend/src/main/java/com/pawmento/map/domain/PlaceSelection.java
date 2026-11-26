package com.pawmento.map.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 영역 안의 후보 장소에서 화면에 줄 장소를 고른다.
 * 순서는 ① 요청한 종류만 남기기 ② (병원과 미용을 둘 다 요청했을 때만) 겹치는 미용 지우기 ③ 영역 가운데에 가까운 순 정렬
 * ④ 최대 개수로 자르기다. 잘렸는지(truncated)는 겹침을 지운 뒤의 개수로 판단한다.
 */
public final class PlaceSelection {

	// 같은 이름(띄어쓰기·괄호·구두점·대소문자는 무시)의 병원과 미용이 이 거리(m) 안에 있으면 같은 곳으로 본다
	static final double OVERLAP_METERS = 30;

	private static final double EARTH_RADIUS_METERS = 6_371_000;

	// 이름 비교에서 무시하는 글자(띄어쓰기·괄호·구두점). 장소마다 부르므로 한 번만 컴파일해 둔다(검색은 후보 수천 곳에 쓴다, #195)
	private static final Pattern IGNORED_IN_NAME = Pattern.compile("[\\s()\\[\\]{}·\\-_.,'\"]");

	private PlaceSelection() {
	}

	public record Selected(List<Place> places, boolean truncated) {
	}

	public static Selected select(List<Place> candidates, MapArea area, Set<PlaceCategory> requested, int limit) {
		List<Place> requestedOnly = candidates.stream().filter(place -> requested.contains(place.category())).toList();
		List<Place> kept = requested.contains(PlaceCategory.HOSPITAL) && requested.contains(PlaceCategory.GROOMING)
				? withoutOverlappingGrooming(requestedOnly) : requestedOnly;

		double centerLatitude = (area.getMinLatitude() + area.getMaxLatitude()) / 2;
		double centerLongitude = (area.getMinLongitude() + area.getMaxLongitude()) / 2;
		List<Place> sorted = new ArrayList<>(kept);
		sorted.sort(Comparator
			.comparingDouble((Place place) -> meters(centerLatitude, centerLongitude, place.latitude(), place.longitude()))
			.thenComparing(Place::id));

		boolean truncated = sorted.size() > limit;
		return new Selected(truncated ? List.copyOf(sorted.subList(0, limit)) : List.copyOf(sorted), truncated);
	}

	// 영역 조회와 검색(PlaceSearch)이 같은 기준으로 겹침을 지운다(#172-S15, #195-S4)
	static List<Place> withoutOverlappingGrooming(List<Place> places) {
		Map<String, List<Place>> hospitalsByName = new HashMap<>();
		for (Place place : places) {
			if (place.category() == PlaceCategory.HOSPITAL) {
				hospitalsByName.computeIfAbsent(comparableName(place.name()), name -> new ArrayList<>()).add(place);
			}
		}
		return places.stream().filter(place -> !isOverlappingGrooming(place, hospitalsByName)).toList();
	}

	private static boolean isOverlappingGrooming(Place place, Map<String, List<Place>> hospitalsByName) {
		if (place.category() != PlaceCategory.GROOMING) {
			return false;
		}
		String name = comparableName(place.name());
		// 비교할 글자가 없는 이름(공백·구두점뿐)은 서로 같다고 볼 근거가 없다
		if (name.isEmpty()) {
			return false;
		}
		return hospitalsByName.getOrDefault(name, List.of())
			.stream()
			.anyMatch(hospital -> meters(hospital.latitude(), hospital.longitude(), place.latitude(),
					place.longitude()) <= OVERLAP_METERS);
	}

	// 같은 가게가 병원과 미용에 띄어쓰기만 다르게 등록된 경우가 많아(예: 스테이동물병원 / 스테이 동물병원) 공백·괄호·구두점을 빼고
	// 소문자로 맞춰 비교한다. 진짜 데이터에서 이 비교로 253곳이 더 정리되었고 표본은 모두 같은 가게였다
	static String comparableName(String name) {
		return IGNORED_IN_NAME.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
	}

	// 두 좌표 사이의 거리(m). 하버사인 공식이다
	static double meters(double latitude1, double longitude1, double latitude2, double longitude2) {
		double dLat = Math.toRadians(latitude2 - latitude1);
		double dLng = Math.toRadians(longitude2 - longitude1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latitude1))
				* Math.cos(Math.toRadians(latitude2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
	}

}
