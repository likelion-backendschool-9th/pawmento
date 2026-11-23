package com.pawmento.map.application.port.in;

/**
 * 공공데이터에서 장소를 가져오는 요청.
 *
 * @param category 가져올 종류의 이름. HOSPITAL 또는 GROOMING(보호센터는 다른 API라 따로 가져온다)
 */
public record SyncLocalDataPlacesCommand(String category) {
}
