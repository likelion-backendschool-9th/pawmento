package com.pawmento.map.application.port.in;

import java.util.List;

import com.pawmento.map.domain.Place;

/**
 * 영역 안의 장소 목록.
 *
 * @param places 영역 가운데에 가까운 순으로, 최대 500곳
 * @param truncated 겹침을 지운 뒤에도 500곳보다 많아서 잘렸으면 true
 */
public record ListPlacesResult(List<Place> places, boolean truncated) {
}
