package com.pawmento.map.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.pawmento.map.domain.exception.InvalidSearchQueryException;

/**
 * 검색어로 찾은 후보 장소에서 화면에 줄 장소를 고른다(#195-R1).
 * 순서는 ① 요청한 종류만 남기기 ② (병원과 미용을 둘 다 요청했을 때만) 겹치는 미용 지우기 ③ 순서 정하기 ④ 최대 개수로 자르기다.
 * 순서는 지도 중심을 받았으면 중심에서 가까운 순이고, 아니면 이름이 검색어로 시작하는 곳, 이름에 검색어가 든 곳,
 * 주소에만 든 곳 순이며 같은 묶음 안에서는 이름순이다. 잘렸는지(truncated)는 겹침을 지운 뒤의 개수로 판단한다.
 */
public final class PlaceSearch {

	/** 정규화한 검색어의 길이 범위. 한 글자는 맞는 곳이 너무 많고 30자는 이름·주소 일부로 충분하다(#195). */
	public static final int MIN_QUERY_LENGTH = 2;

	public static final int MAX_QUERY_LENGTH = 30;

	private PlaceSearch() {
	}

	/** 검색 결과 한 곳. 지도 중심을 받지 않았으면 distanceMeters는 null이다. */
	public record Hit(Place place, Integer distanceMeters) {
	}

	public record Result(List<Hit> hits, boolean truncated) {
	}

	/** 지도 중심. 위도와 경도는 함께 있어야 한다. */
	public record Center(double latitude, double longitude) {
	}

	/**
	 * 검색어를 이름·주소 비교와 같은 기준으로 정규화한다(띄어쓰기·괄호·구두점·대소문자 무시). 길이는 정규화한 뒤에 센다.
	 * 공백·구두점뿐인 검색어는 빈 값이 되어 너무 짧다고, 제어 문자가 든 검색어는 쓸 수 없는 글자가 있다고 거절한다.
	 */
	public static String normalize(String query) {
		String key = PlaceSelection.comparableName(query == null ? "" : query.strip());
		// 널 문자(U+0000) 같은 제어 문자는 DB가 LIKE의 글자로 받지 않아 500이 된다. DB를 부르기 전에 400으로 거절한다.
		// 띄어쓰기·탭·줄바꿈은 정규화에서 이미 지워졌다
		if (key.chars().anyMatch(Character::isISOControl)) {
			throw InvalidSearchQueryException.invalidCharacter();
		}
		if (key.codePointCount(0, key.length()) < MIN_QUERY_LENGTH) {
			throw InvalidSearchQueryException.tooShort(MIN_QUERY_LENGTH);
		}
		if (key.codePointCount(0, key.length()) > MAX_QUERY_LENGTH) {
			throw InvalidSearchQueryException.tooLong(MAX_QUERY_LENGTH);
		}
		return key;
	}

	/**
	 * @param candidates 정규화한 검색어가 이름이나 주소에 든 장소(종류는 요청한 것만 와도 되고 여기서도 한 번 더 거른다)
	 * @param key {@link #normalize}한 검색어
	 * @param center 지도 중심(없을 수 있다)
	 */
	public static Result select(List<Place> candidates, String key, Set<PlaceCategory> requested,
			Optional<Center> center, int limit) {
		List<Place> requestedOnly = candidates.stream().filter(place -> requested.contains(place.category())).toList();
		List<Place> kept = requested.contains(PlaceCategory.HOSPITAL) && requested.contains(PlaceCategory.GROOMING)
				? PlaceSelection.withoutOverlappingGrooming(requestedOnly) : requestedOnly;

		// 정렬에 쓰는 값(거리, 순위)은 장소마다 한 번만 구한다. 비교할 때마다 구하면 후보가 수천 곳일 때(예: 검색어 "서울")
		// 이름 정규화가 정렬 횟수(n log n)만큼 불려 느려진다
		List<Ranked> ranked = new ArrayList<>();
		for (Place place : kept) {
			double exact = center
				.map(c -> PlaceSelection.meters(c.latitude(), c.longitude(), place.latitude(), place.longitude()))
				.orElse(Double.NaN);
			int rank = center.isPresent() ? 0 : rank(place, key);
			ranked.add(new Ranked(new Hit(place, center.isPresent() ? (int) Math.round(exact) : null), exact, rank));
		}
		// 지도 중심이 있으면 반올림하기 전의 정확한 거리로 정렬한다. 같은 거리끼리는 id 순서라 결과가 늘 같다
		ranked.sort(center.isPresent()
				? Comparator.comparingDouble(Ranked::exact).thenComparing(r -> r.hit().place().id())
				: Comparator.comparingInt(Ranked::rank)
					.thenComparing(r -> r.hit().place().name())
					.thenComparing(r -> r.hit().place().id()));
		List<Hit> hits = ranked.stream().map(Ranked::hit).toList();

		boolean truncated = hits.size() > limit;
		return new Result(truncated ? List.copyOf(hits.subList(0, limit)) : List.copyOf(hits), truncated);
	}

	// 정렬에만 쓰는 값: 정확한 거리(중심이 없으면 NaN)와 검색어와의 관련 순위(중심이 있으면 쓰지 않아 0)
	private record Ranked(Hit hit, double exact, int rank) {
	}

	// 0: 이름이 검색어로 시작, 1: 이름에 검색어가 있음, 2: 주소에만 있음
	private static int rank(Place place, String key) {
		String name = place.nameKey();
		if (name.startsWith(key)) {
			return 0;
		}
		return name.contains(key) ? 1 : 2;
	}

}
