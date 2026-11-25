package com.pawmento.map.application.port.in;

/**
 * 관리자가 장소 가져오기를 시작하는 요청.
 *
 * @param requestedBy 요청한 관리자 회원. 현재 관리자인지 확인한다
 * @param category 가져올 종류의 이름(HOSPITAL, GROOMING)
 */
public record StartPlaceSyncCommand(Long requestedBy, String category) {
}
