package com.pawmento.pet.application.port.in;

import java.io.InputStream;
import java.util.function.Supplier;

public interface GetPetProfilePhotoUseCase {

	Photo get(Query query);

	record Query(Long memberId, Long petId, Long assetId, boolean thumbnail) {
	}

	record Photo(String filename, String mimeType, long sizeBytes, Supplier<InputStream> content) {
	}

}
