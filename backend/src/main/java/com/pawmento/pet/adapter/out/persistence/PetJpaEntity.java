package com.pawmento.pet.adapter.out.persistence;

import java.time.LocalDate;

import com.pawmento.pet.application.exception.StoredPetDataInvalidException;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetMode;
import com.pawmento.pet.domain.PetSpecies;
import com.pawmento.pet.domain.exception.InvalidStoredBirthInformationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pets", uniqueConstraints = @UniqueConstraint(name = "uk_pets_owner_tag",
		columnNames = { "owner_member_id", "tag" }),
		indexes = @Index(name = "ix_pets_profile_photo_asset", columnList = "profile_photo_asset_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long ownerMemberId;

	@Column(nullable = false, length = 20)
	private String name;

	@Column(nullable = false, length = 16)
	private String tag;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PetSpecies species;

	@Column(length = 100)
	private String breed;

	private LocalDate birthday;

	private Integer birthYear;

	private Long profilePhotoAssetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PetMode mode;

	@Version
	private Long version;

	public static PetJpaEntity from(Pet pet) {
		PetJpaEntity entity = new PetJpaEntity();
		entity.id = pet.getId();
		entity.ownerMemberId = pet.getOwnerMemberId();
		entity.name = pet.getName();
		entity.tag = pet.getTag();
		entity.species = pet.getSpecies();
		entity.breed = pet.getBreed();
		entity.birthday = pet.getBirthday();
		entity.birthYear = pet.getBirthYear();
		entity.profilePhotoAssetId = pet.getProfilePhotoAssetId();
		entity.mode = pet.getMode();
		entity.version = pet.getVersion();
		return entity;
	}

	public Pet toDomain() {
		try {
			return Pet.restore(id, ownerMemberId, name, tag, species, breed, birthday, mode, version,
					profilePhotoAssetId, birthYear);
		} catch (InvalidStoredBirthInformationException e) {
			throw new StoredPetDataInvalidException(id, e);
		}
	}

}
