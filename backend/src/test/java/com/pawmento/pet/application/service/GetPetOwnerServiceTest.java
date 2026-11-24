package com.pawmento.pet.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.pet.application.port.in.*;
import com.pawmento.pet.application.port.out.*;
import com.pawmento.pet.domain.*;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GetPetOwnerServiceTest {
  @ParameterizedTest
  @EnumSource(PetMode.class)
  @DisplayName("#133-S1 소유주 조회와 잠금 조회는 소유주·모드를 같은 pet 스냅샷에서 반환한다")
  void returnsModeFromSamePet(PetMode mode) {
    var load = mock(LoadPetPort.class);
    var locks = mock(LockPetPort.class);
    var service = new GetPetOwnerService(load, locks);
    var pet = Pet.restore(10L, 1L, "보리", "1234", PetSpecies.DOG, null, null, mode, 0L);
    when(load.findById(10L)).thenReturn(Optional.of(pet));
    when(locks.lock(10L)).thenReturn(Optional.of(pet));

    var expected = Optional.of(new GetPetOwnerResult(1L, mode.name()));
    assertThat(service.get(new GetPetOwnerQuery(10L))).isEqualTo(expected);
    assertThat(service.lock(new LockPetOwnerQuery(10L))).isEqualTo(expected);
    when(load.findById(11L)).thenReturn(Optional.empty());
    when(locks.lock(11L)).thenReturn(Optional.empty());
    assertThat(service.get(new GetPetOwnerQuery(11L))).isEmpty();
    assertThat(service.lock(new LockPetOwnerQuery(11L))).isEmpty();
  }
}
