package com.pawmento.pet.adapter.in.web;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.pawmento.pet.application.port.in.GetPetProfilePhotoUseCase;
import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;
import com.pawmento.pet.application.port.in.ListPetsQuery;
import com.pawmento.pet.application.port.in.ListPetsUseCase;
import com.pawmento.pet.application.port.in.UpdatePetProfilePhotoUseCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pets")
@RequiredArgsConstructor
public class PetController {

	private final CreatePetUseCase createPetUseCase;
	private final ListPetsUseCase listPetsUseCase;
	private final GetPetUseCase getPetUseCase;
	private final EditPetUseCase editPetUseCase;
	private final ChangePetModeUseCase changePetModeUseCase;
	private final DeletePetUseCase deletePetUseCase;
	private final UpdatePetProfilePhotoUseCase updatePetProfilePhotoUseCase;
	private final GetPetProfilePhotoUseCase getPetProfilePhotoUseCase;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PetResponse create(@LoginMember Long memberId, @Valid @RequestBody CreatePetRequest request) {
		return PetResponse.from(createPetUseCase.create(new CreatePetCommand(memberId, request.name(), request.species(),
				request.breed(), request.birthday(), PetBirthYearRequest.parse(request.birthYear()))));
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
				request.breed(), request.birthday(), PetBirthYearRequest.parse(request.birthYear()))));
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

	@PutMapping("/{petId}/profile-photo")
	PetResponse updateProfilePhoto(@LoginMember Long memberId, @PathVariable Long petId,
			@RequestBody UpdatePetProfilePhotoRequest request) {
		return PetResponse.from(updatePetProfilePhotoUseCase.update(
				new UpdatePetProfilePhotoUseCase.Command(memberId, petId, request.assetId())));
	}

	@DeleteMapping("/{petId}/profile-photo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeProfilePhoto(@LoginMember Long memberId, @PathVariable Long petId) {
		updatePetProfilePhotoUseCase.update(new UpdatePetProfilePhotoUseCase.Command(memberId, petId, null));
	}

	@GetMapping("/{petId}/profile-photo/{assetId}/content")
	ResponseEntity<Resource> profilePhoto(@LoginMember Long memberId, @PathVariable Long petId,
			@PathVariable Long assetId) {
		return profilePhotoResponse(new GetPetProfilePhotoUseCase.Query(memberId, petId, assetId, false));
	}

	@GetMapping("/{petId}/profile-photo/{assetId}/thumbnail")
	ResponseEntity<Resource> profilePhotoThumbnail(@LoginMember Long memberId, @PathVariable Long petId,
			@PathVariable Long assetId) {
		return profilePhotoResponse(new GetPetProfilePhotoUseCase.Query(memberId, petId, assetId, true));
	}

	private ResponseEntity<Resource> profilePhotoResponse(GetPetProfilePhotoUseCase.Query query) {
		var photo = getPetProfilePhotoUseCase.get(query);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(photo.mimeType()))
				.contentLength(photo.sizeBytes())
				.cacheControl(CacheControl.noStore().cachePrivate())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(photo.filename()).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.body(new PetProfilePhotoResource(photo));
	}

}
