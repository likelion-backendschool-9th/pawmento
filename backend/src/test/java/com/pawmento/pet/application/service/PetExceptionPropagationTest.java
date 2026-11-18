package com.pawmento.pet.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.global.exception.RuleViolationException;
import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.EditPetCommand;
import com.pawmento.pet.application.port.in.ChangePetModeCommand;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetMode;
import com.pawmento.pet.domain.exception.InvalidPetProfileException;

class PetExceptionPropagationTest {
	private final SavePetPort save = mock(SavePetPort.class);
	private final LoadPetPort load = mock(LoadPetPort.class);
	private final Clock clock = Clock.fixed(java.time.Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC);

	@Test
	@DisplayName("#72-S1 생성 입력 오류는 도메인 기본 예외로 전파되고 저장하지 않는다")
	void createRejectsInvalidProfile() {
		PetTagGenerator tags = mock(PetTagGenerator.class);
		when(tags.nextTag(1L)).thenReturn("abcd");
		var service = new CreatePetService(save, tags, clock);
		assertThatThrownBy(() -> service.create(new CreatePetCommand(1L, " ", "DOG", null, null)))
			.isInstanceOf(InvalidPetProfileException.class).isInstanceOf(RuleViolationException.class)
			.hasMessage("이름은 1~20자로 입력해 주세요.");
		verifyNoInteractions(save);
	}

	@Test
	@DisplayName("#72-S1 수정의 도메인 입력 예외는 감싸지 않고 저장하지 않는다")
	void editPreservesException() {
		Pet pet = mock(Pet.class);
		when(load.findById(2L)).thenReturn(Optional.of(pet));
		var failure = InvalidPetProfileException.invalidName();
		doThrow(failure).when(pet).edit(eq(" "), isNull(), isNull(), isNull(), any(LocalDate.class));
		assertThatThrownBy(() -> new EditPetService(load, save, clock)
			.edit(new EditPetCommand(1L, 2L, " ", null, null, null))).isSameAs(failure);
		verifyNoInteractions(save);
	}

	@Test
	@DisplayName("#72-S1 모드 변경의 도메인 예외는 감싸지 않고 저장하지 않는다")
	void modePreservesException() {
		Pet pet = mock(Pet.class);
		when(load.findById(2L)).thenReturn(Optional.of(pet));
		var failure = InvalidPetProfileException.invalidMode();
		doThrow(failure).when(pet).changeMode(PetMode.ACTIVE);
		assertThatThrownBy(() -> new ChangePetModeService(load, save)
			.changeMode(new ChangePetModeCommand(1L, 2L, "ACTIVE"))).isSameAs(failure);
		verifyNoInteractions(save);
	}

	@Test
	@DisplayName("#72-S1 저장 장애는 입력 오류로 바뀌지 않는다")
	void storageFailureIsNotValidation() {
		PetTagGenerator tags = mock(PetTagGenerator.class);
		when(tags.nextTag(1L)).thenReturn("abcd");
		var failure = new IllegalStateException("storage unavailable");
		when(save.save(any(Pet.class))).thenThrow(failure);
		assertThatThrownBy(() -> new CreatePetService(save, tags, clock)
			.create(new CreatePetCommand(1L, "콩이", "DOG", null, null))).isSameAs(failure);
	}

	@Test
	@DisplayName("#72-S1 문자열 종류와 모드의 파싱 오류는 기본 입력 예외와 기존 메시지를 유지한다")
	void parserErrorsRemainValidation() {
		for (String value : new String[] {null, " ", "UNKNOWN"}) {
			assertThatThrownBy(() -> PetSpeciesParser.require(value)).isInstanceOf(InvalidPetException.class)
				.isInstanceOf(RuleViolationException.class).hasMessage("반려동물의 종류를 선택해 주세요.");
			assertThatThrownBy(() -> PetModeParser.require(value)).isInstanceOf(InvalidPetException.class)
				.isInstanceOf(RuleViolationException.class).hasMessage("모드를 선택해 주세요.");
		}
	}
}
