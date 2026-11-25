package com.pawmento.map.application.port.out;

import java.util.Optional;

/**
 * 좌표에 해당하는 도로명 주소(도로명과 건물번호)를 외부 서비스에서 조회한다. 지번 주소로는 돌아가지 않는다.
 * 도로명 주소나 건물번호가 없거나 조회에 실패하면 빈 값을 돌려준다.
 * 지번으로 돌아가는 조회는 {@link LoadAddressPort}다. 일기 위치(#19)가 그쪽을 쓴다.
 */
public interface LoadRoadAddressPort {

	Optional<String> loadRoadAddressByCoordinates(double latitude, double longitude);

}
