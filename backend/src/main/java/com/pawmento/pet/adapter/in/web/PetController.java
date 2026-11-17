package com.pawmento.pet.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.LoginMember;
import com.pawmento.pet.application.port.in.ChangePetModeCommand;
import com.pawmento.pet.application.port.in.ChangePetModeUseCase;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;
import com.pawmento.pet.application.port.in.DeletePetCommand;
import com.pawmento.pet.application.port.in.DeletePetUseCase;
import com.pawmento.pet.application.port.in.EditPetCommand;
import com.pawmento.pet.application.port.in.EditPetUseCase;
import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;
import com.pawmento.pet.application.port.in.ListPetsQuery;
import com.pawmento.pet.application.port.in.ListPetsUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/pets")
public class PetController {

	private final CreatePetUseCase createPetUseCase;
	private final ListPetsUseCase listPetsUseCase;
	private final GetPetUseCase getPetUseCase;
	private final EditPetUseCase editPetUseCase;
	private final ChangePetModeUseCase changePetModeUseCase;
	private final DeletePetUseCase deletePetUseCase;

	public PetController(CreatePetUseCase createPetUseCase, ListPetsUseCase listPetsUseCase, GetPetUseCase getPetUseCase,
			EditPetUseCase editPetUseCase, ChangePetModeUseCase changePetModeUseCase,
			DeletePetUseCase deletePetUseCase) {
		this.createPetUseCase = createPetUseCase;
		this.listPetsUseCase = listPetsUseCase;
		this.getPetUseCase = getPetUseCase;
		this.editPetUseCase = editPetUseCase;
		this.changePetModeUseCase = changePetModeUseCase;
		this.deletePetUseCase = deletePetUseCase;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PetResponse create(@LoginMember Long memberId, @Valid @RequestBody CreatePetRequest request) {
		return PetResponse.from(createPetUseCase.create(new CreatePetCommand(memberId, request.name(), request.species(),
				request.breed(), request.birthday())));
	}

	@GetMapping
	List<PetResponse> list(@LoginMember Long memberId) {
		return listPetsUseCase.list(new ListPetsQuery(memberId)).stream().map(PetResponse::from).toList();
	}

	@GetMapping("/{petId}")
	PetResponse get(@LoginMember Long memberId, @PathVariable Long petId) {
		return PetResponse.from(getPetUseCase.get(new GetPetQuery(memberId, petId)));
	}

	@PatchMapping("/{petId}")
	PetResponse edit(@LoginMember Long memberId, @PathVariable Long petId, @Valid @RequestBody EditPetRequest request) {
		return PetResponse.from(editPetUseCase.edit(new EditPetCommand(memberId, petId, request.name(), request.species(),
				request.breed(), request.birthday())));
	}

	@PatchMapping("/{petId}/mode")
	PetResponse changeMode(@LoginMember Long memberId, @PathVariable Long petId,
			@Valid @RequestBody ChangePetModeRequest request) {
		return PetResponse.from(
				changePetModeUseCase.changeMode(new ChangePetModeCommand(memberId, petId, request.mode())));
	}

	@DeleteMapping("/{petId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@LoginMember Long memberId, @PathVariable Long petId) {
		deletePetUseCase.delete(new DeletePetCommand(memberId, petId));
	}

}
