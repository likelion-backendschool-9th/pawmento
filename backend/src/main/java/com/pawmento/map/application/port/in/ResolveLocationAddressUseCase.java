package com.pawmento.map.application.port.in;

import java.util.Optional;

public interface ResolveLocationAddressUseCase {
  Optional<String> resolve(double latitude, double longitude);
}
