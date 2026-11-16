package com.pawmento.map.domain;

/**
 * 지도 화면에 보이는 사각형 영역. 최솟값이 최댓값보다 크면 만들 수 없다.
 * 날짜 변경선을 걸친 영역(경도의 최솟값이 최댓값보다 큰 경우)은 지원하지 않는다.
 */
public class MapArea {

	private final double minLatitude;
	private final double minLongitude;
	private final double maxLatitude;
	private final double maxLongitude;

	private MapArea(double minLatitude, double minLongitude, double maxLatitude, double maxLongitude) {
		Coordinates.requireLatitude(minLatitude);
		Coordinates.requireLatitude(maxLatitude);
		Coordinates.requireLongitude(minLongitude);
		Coordinates.requireLongitude(maxLongitude);
		if (minLatitude > maxLatitude || minLongitude > maxLongitude) {
			throw new IllegalArgumentException("영역의 최솟값은 최댓값보다 클 수 없습니다.");
		}
		this.minLatitude = minLatitude;
		this.minLongitude = minLongitude;
		this.maxLatitude = maxLatitude;
		this.maxLongitude = maxLongitude;
	}

	public static MapArea of(double minLatitude, double minLongitude, double maxLatitude, double maxLongitude) {
		return new MapArea(minLatitude, minLongitude, maxLatitude, maxLongitude);
	}

	public double getMinLatitude() {
		return minLatitude;
	}

	public double getMinLongitude() {
		return minLongitude;
	}

	public double getMaxLatitude() {
		return maxLatitude;
	}

	public double getMaxLongitude() {
		return maxLongitude;
	}

}
