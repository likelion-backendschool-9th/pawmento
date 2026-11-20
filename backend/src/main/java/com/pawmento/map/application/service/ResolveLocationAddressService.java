package com.pawmento.map.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.ResolveLocationAddressUseCase;
import com.pawmento.map.application.port.out.LoadAddressPort;
import com.pawmento.map.domain.DiaryLocation;
import com.pawmento.map.domain.DiaryLocationValue;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResolveLocationAddressService implements ResolveLocationAddressUseCase {
	private final LoadAddressPort address;

	@Override
	@Transactional(propagation = Propagation.NEVER)
	public Optional<String> resolve(double latitude, double longitude) {
		var value = new DiaryLocationValue(latitude, longitude, null);
		return address.loadByCoordinates(value.latitude(), value.longitude())
			.map(String::strip)
			.filter(name -> !name.isEmpty() && name.length() <= DiaryLocation.PLACE_NAME_MAX_LENGTH);
	}
}
