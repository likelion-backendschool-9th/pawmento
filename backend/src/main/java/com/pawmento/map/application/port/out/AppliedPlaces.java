package com.pawmento.map.application.port.out;

// 한 종류를 통째로 반영한 결과 건수
public record AppliedPlaces(int inserted, int updated, int deleted) {
}
