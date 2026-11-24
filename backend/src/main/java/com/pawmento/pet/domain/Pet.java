package com.pawmento.pet.domain;

import java.time.LocalDate;

import com.pawmento.pet.domain.exception.InvalidPetProfileException;
import com.pawmento.pet.domain.exception.PetOwnershipViolationException;
import com.pawmento.pet.domain.exception.InvalidStoredBirthInformationException;

import lombok.Getter;

// 반려동물 프로필. Spring·JPA를 모른다. 생성·수정·모드 규칙을 여기서 지킨다
@Getter
public class Pet {

	private Long id;
	private Long ownerMemberId;
	private String name;
	private String tag;
	private PetSpecies species;
	private String breed;
	private LocalDate birthday;
	// 정확한 날짜가 없을 때만 저장한다.
	private Integer birthYear;
	private Long profilePhotoAssetId;
	private PetMode mode;
	// 낙관적 잠금용. DB에서 읽은 버전을 저장까지 그대로 나른다
	private Long version;

	private Pet(Long ownerMemberId, String name, String tag, PetSpecies species, String breed, LocalDate birthday,
			PetMode mode) {
		this.ownerMemberId = ownerMemberId;
		this.name = name;
		this.tag = tag;
		this.species = species;
		this.breed = breed;
		this.birthday = birthday;
		this.mode = mode;
	}

	public static Pet create(Long ownerMemberId, String name, String tag, PetSpecies species, String breed,
			LocalDate birthday, LocalDate today) {
		return create(ownerMemberId, name, tag, species, breed, birthday, null, today);
	}

	public static Pet create(Long ownerMemberId, String name, String tag, PetSpecies species, String breed,
			LocalDate birthday, Integer birthYear, LocalDate today) {
		String trimmedName = requireName(name);
		requireOwner(ownerMemberId);
		requireTag(tag);
		requireSpecies(species);
		requireBirthInformation(birthday, birthYear, today);
		Pet pet = new Pet(ownerMemberId, trimmedName, tag, species, normalizeBreed(breed), birthday, PetMode.ACTIVE);
		pet.birthYear = birthday == null ? birthYear : null;
		return pet;
	}

	// 기존 날짜는 보존하고, 날짜와 연도를 동시에 저장할 수 없다는 불변 조건만 검사한다.
	public static Pet restore(Long id, Long ownerMemberId, String name, String tag, PetSpecies species, String breed,
			LocalDate birthday, PetMode mode, Long version) {
		return restore(id, ownerMemberId, name, tag, species, breed, birthday, mode, version, null);
	}

	public static Pet restore(Long id, Long ownerMemberId, String name, String tag, PetSpecies species, String breed,
			LocalDate birthday, PetMode mode, Long version, Long profilePhotoAssetId) {
		return restore(id, ownerMemberId, name, tag, species, breed, birthday, mode, version, profilePhotoAssetId, null);
	}

	public static Pet restore(Long id, Long ownerMemberId, String name, String tag, PetSpecies species, String breed,
			LocalDate birthday, PetMode mode, Long version, Long profilePhotoAssetId, Integer birthYear) {
		if (birthday != null && birthYear != null) {
			throw new InvalidStoredBirthInformationException();
		}
		Pet pet = new Pet(ownerMemberId, name, tag, species, breed, birthday, mode);
		pet.birthYear = birthYear;
		pet.id = id;
		pet.version = version;
		pet.profilePhotoAssetId = profilePhotoAssetId;
		return pet;
	}

	// null인 항목은 바꾸지 않는다. 비운 품종은 빈 문자열로 보낸다
	public void edit(String name, PetSpecies species, String breed, LocalDate birthday, LocalDate today) {
		edit(name, species, breed, birthday, null, today);
	}

	public void edit(String name, PetSpecies species, String breed, LocalDate birthday, Integer birthYear, LocalDate today) {
		// 응답에 포함된 1900년 이전 연도를 다시 보내도 저장된 정확한 날짜는 보존한다.
		boolean keepsLegacyBirthday = this.birthday != null && birthYear != null
				&& birthYear < 1900 && this.birthday.getYear() == birthYear
				&& (birthday == null || this.birthday.equals(birthday));
		requireBirthInformation(birthday, keepsLegacyBirthday ? null : birthYear, today);
		if (name != null) {
			this.name = requireName(name);
		}
		if (species != null) {
			this.species = species;
		}
		if (breed != null) {
			this.breed = normalizeBreed(breed);
		}
		if (birthday != null) {
			this.birthday = birthday;
			this.birthYear = null;
		} else if (birthYear != null && !birthYear.equals(effectiveBirthYear())) {
			this.birthday = null;
			this.birthYear = birthYear;
		}
	}

	public Integer effectiveBirthYear() {
		if (birthday == null) {
			return birthYear;
		}
		return birthday.getYear();
	}

	public Integer ageByYear(LocalDate today) {
		Integer year = effectiveBirthYear();
		return year == null ? null : today.getYear() - year;
	}

	private static void requireBirthInformation(LocalDate birthday, Integer birthYear, LocalDate today) {
		requireBirthdayNotFuture(birthday, today);
		if (birthYear != null) {
			if (birthYear < 1900 || birthYear > today.getYear()) {
				throw InvalidPetProfileException.invalidBirthYear();
			}
			if (birthday != null && birthday.getYear() != birthYear) {
				throw InvalidPetProfileException.inconsistentBirthYear();
			}
		}
	}

	public void changeProfilePhoto(Long assetId) {
		this.profilePhotoAssetId = assetId;
	}

	public void changeMode(PetMode mode) {
		if (mode == null) {
			throw InvalidPetProfileException.invalidMode();
		}
		this.mode = mode;
	}

	public void requireOwnedBy(Long memberId) {
		if (!ownedBy(memberId)) {
			throw new PetOwnershipViolationException();
		}
	}

	public void changeOwner(Long newOwnerMemberId, String tag) {
		requireOwner(newOwnerMemberId);
		requireTag(tag);
		this.ownerMemberId = newOwnerMemberId;
		this.tag = tag;
	}

	public boolean ownedBy(Long memberId) {
		return ownerMemberId.equals(memberId);
	}

	private static void requireOwner(Long ownerMemberId) {
		if (ownerMemberId == null) {
			throw InvalidPetProfileException.missingOwner();
		}
	}

	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw InvalidPetProfileException.invalidName();
		}
		String trimmed = name.strip();
		if (trimmed.isEmpty() || trimmed.length() > 20) {
			throw InvalidPetProfileException.invalidName();
		}
		return trimmed;
	}

	private static void requireTag(String tag) {
		if (tag == null || tag.isBlank()) {
			throw InvalidPetProfileException.missingTag();
		}
	}

	private static void requireSpecies(PetSpecies species) {
		if (species == null) {
			throw InvalidPetProfileException.invalidSpecies();
		}
	}

	private static void requireBirthdayNotFuture(LocalDate birthday, LocalDate today) {
		if (birthday != null && birthday.isAfter(today)) {
			throw InvalidPetProfileException.futureBirthday();
		}
	}

	private static String normalizeBreed(String breed) {
		if (breed == null || breed.isBlank()) {
			return null;
		}
		return breed.strip();
	}

}
