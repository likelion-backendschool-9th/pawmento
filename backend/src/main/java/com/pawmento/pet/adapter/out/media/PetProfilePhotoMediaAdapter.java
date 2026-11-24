package com.pawmento.pet.adapter.out.media;

import java.util.List;

import org.springframework.stereotype.Component;

import com.pawmento.media.application.port.in.ChangeMediaReferencesCommand;
import com.pawmento.media.application.port.in.ChangeMediaReferencesUseCase;
import com.pawmento.media.application.port.in.GetAttachedMediaQuery;
import com.pawmento.media.application.port.in.GetAttachedMediaUseCase;
import com.pawmento.media.application.port.in.LockMediaReferencesCommand;
import com.pawmento.media.application.port.in.LockMediaReferencesUseCase;
import com.pawmento.pet.application.port.in.GetPetProfilePhotoUseCase.Photo;
import com.pawmento.pet.application.port.out.PetProfilePhotoMediaPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PetProfilePhotoMediaAdapter implements PetProfilePhotoMediaPort {

	private static final String PURPOSE = "PET_PROFILE";

	private final LockMediaReferencesUseCase lockReferences;
	private final ChangeMediaReferencesUseCase changeReferences;
	private final GetAttachedMediaUseCase attachedMedia;

	@Override
	public void lock(Long memberId, Long petId, List<Long> previous, List<Long> requested) {
		lockReferences.lock(new LockMediaReferencesCommand(memberId, petId, previous, requested, PURPOSE));
	}

	@Override
	public void changeReferences(Long memberId, Long petId, List<Long> previous, List<Long> requested) {
		changeReferences.change(new ChangeMediaReferencesCommand(memberId, petId, previous, requested, PURPOSE));
	}

	@Override
	public Photo read(Long petId, Long assetId, boolean thumbnail) {
		var file = attachedMedia.get(new GetAttachedMediaQuery(petId, assetId, thumbnail, PURPOSE));
		return new Photo(file.filename(), file.mimeType(), file.sizeBytes(), file.content());
	}

}
