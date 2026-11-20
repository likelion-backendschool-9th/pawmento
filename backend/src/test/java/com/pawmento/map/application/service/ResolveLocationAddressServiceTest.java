package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.map.application.port.out.LoadAddressPort;
import java.util.Optional;
import org.junit.jupiter.api.*;

class ResolveLocationAddressServiceTest {
  @Test
  @DisplayName("#44-S7 서버 주소는 255자 경계를 검사해 초과·공백은 비사용이고 내부 오류는 숨기지 않는다")
  void boundaries() {
    var port = mock(LoadAddressPort.class);
    var service = new ResolveLocationAddressService(port);
    for (String text : new String[] {" ", "가".repeat(256), "🐾".repeat(128)}) {
      when(port.loadByCoordinates(0, -73.5)).thenReturn(Optional.of(text));
      assertThat(service.resolve(-0.0, -73.5)).isEmpty();
    }
    when(port.loadByCoordinates(0, -73.5)).thenReturn(Optional.of(" " + "가".repeat(255) + " "));
    assertThat(service.resolve(0, -73.5)).contains("가".repeat(255));
    when(port.loadByCoordinates(0, -73.5)).thenThrow(new IllegalStateException("bug"));
    assertThatThrownBy(() -> service.resolve(0, -73.5)).isInstanceOf(IllegalStateException.class);
  }
}
