package com.pawmento.map.domain;

import java.math.BigDecimal;

import com.pawmento.map.domain.exception.InvalidMapAreaException;

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
			throw new InvalidMapAreaException();
		}
		this.minLatitude = minLatitude;
		this.minLongitude = minLongitude;
		this.maxLatitude = maxLatitude;
		this.maxLongitude = maxLongitude;
	}

	public static MapArea of(double minLatitude, double minLongitude, double maxLatitude, double maxLongitude) {
		return new MapArea(minLatitude, minLongitude, maxLatitude, maxLongitude);
	}

	/**
	 * 요청에서 받은 십진수 그대로 순서를 검사한 뒤 double로 바꾼다. double로 바꾼 뒤에 비교하면 1e-400과 0처럼 서로 다른 값이
	 * 같아져서 "최솟값이 최댓값보다 클 수 없다"는 규칙을 통과한다.
	 */
	public static MapArea of(BigDecimal minLatitude, BigDecimal minLongitude, BigDecimal maxLatitude,
			BigDecimal maxLongitude) {
		if (minLatitude.compareTo(maxLatitude) > 0 || minLongitude.compareTo(maxLongitude) > 0) {
			throw new InvalidMapAreaException();
		}
		return new MapArea(minLatitude.doubleValue(), minLongitude.doubleValue(), maxLatitude.doubleValue(),
				maxLongitude.doubleValue());
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
