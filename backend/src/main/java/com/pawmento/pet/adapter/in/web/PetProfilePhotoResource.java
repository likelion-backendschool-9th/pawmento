package com.pawmento.pet.adapter.in.web;

import java.io.InputStream;

import org.springframework.core.io.AbstractResource;

import com.pawmento.pet.application.port.in.GetPetProfilePhotoUseCase.Photo;

final class PetProfilePhotoResource extends AbstractResource {

	private final Photo photo;

	PetProfilePhotoResource(Photo photo) {
		this.photo = photo;
	}

	@Override
	public String getDescription() {
		return "Authenticated pet profile photo";
	}

	@Override
	public String getFilename() {
		return photo.filename();
	}

	@Override
	public long contentLength() {
		return photo.sizeBytes();
	}

	@Override
	public InputStream getInputStream() {
		return photo.content().get();
	}

}
