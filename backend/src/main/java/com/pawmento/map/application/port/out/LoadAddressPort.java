package com.pawmento.map.application.port.out;

import java.util.Optional;

/**
 * 좌표에 해당하는 주소를 외부 서비스에서 조회한다.
 * 조회에 실패하거나 주소가 없으면 빈 값을 돌려준다. 호출한 쪽은 주소 없이도 저장을 이어간다.
 */
public interface LoadAddressPort {

	Optional<String> loadByCoordinates(double latitude, double longitude);

}
