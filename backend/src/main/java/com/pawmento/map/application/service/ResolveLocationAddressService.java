package com.pawmento.map.application.service;

import com.pawmento.map.application.port.in.*;
import com.pawmento.map.application.port.out.LoadAddressPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ResolveLocationAddressService implements ResolveLocationAddressUseCase {
  private final LoadAddressPort address;

  @Override
  @Transactional(propagation = Propagation.NEVER)
  public Optional<String> resolve(double latitude, double longitude) {
    var value = new DiaryLocationValue(latitude, longitude, null);
    return address
        .loadByCoordinates(value.latitude(), value.longitude())
        .map(String::strip)
        .filter(s -> !s.isEmpty() && s.length() <= 255);
  }
}
