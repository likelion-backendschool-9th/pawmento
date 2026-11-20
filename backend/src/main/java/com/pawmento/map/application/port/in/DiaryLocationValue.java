package com.pawmento.map.application.port.in;

public record DiaryLocationValue(double latitude, double longitude, String placeName) {
  public DiaryLocationValue {
    if (!Double.isFinite(latitude)
        || Math.abs(latitude) > 90
        || !Double.isFinite(longitude)
        || Math.abs(longitude) > 180) throw new IllegalArgumentException("내부 위치 좌표가 올바르지 않습니다.");
    latitude = latitude == 0 ? 0 : latitude;
    longitude = longitude == 0 ? 0 : longitude;
    placeName = placeName == null ? null : placeName.strip();
    if (placeName != null && placeName.isEmpty()) placeName = null;
    if (placeName != null && placeName.length() > 255)
      throw new IllegalArgumentException("내부 장소명이 너무 깁니다.");
  }
}
