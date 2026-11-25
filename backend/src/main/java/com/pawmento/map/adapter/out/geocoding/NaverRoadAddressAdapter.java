package com.pawmento.map.adapter.out.geocoding;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.port.out.LoadRoadAddressPort;

/**
 * 장소 카드의 가려진 주소 보완(#239)이 쓰는 도로명 전용 조회. 도로명 주소와 건물번호가 있을 때만 돌려주고
 * 지번 주소로 돌아가지 않는다. 호출과 설정은 {@link NaverAddressAdapter}와 같다.
 * 일기 위치(#19)의 {@code LoadAddressPort}와 빈을 나눠 두어, 한쪽을 테스트에서 가짜로 바꿔도 다른 쪽이 깨지지 않는다.
 */
@Component
class NaverRoadAddressAdapter implements LoadRoadAddressPort {

	private final NaverAddressAdapter delegate;

	@Autowired
	NaverRoadAddressAdapter(NaverMapProperties properties) {
		this(new NaverAddressAdapter(properties));
	}

	// 테스트에서 가짜 서버를 연결한 어댑터를 넣을 수 있게 한다
	NaverRoadAddressAdapter(NaverAddressAdapter delegate) {
		this.delegate = delegate;
	}

	@Override
	public Optional<String> loadRoadAddressByCoordinates(double latitude, double longitude) {
		return delegate.loadRoadAddress(latitude, longitude);
	}

}
