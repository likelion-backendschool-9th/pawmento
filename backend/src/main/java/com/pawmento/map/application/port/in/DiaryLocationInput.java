package com.pawmento.map.application.port.in;

/** 내부 입력 포트의 전달 값. 지도 서비스가 도메인 규칙으로 검증한다. */
public record DiaryLocationInput(double latitude, double longitude, String placeName) {}
