package com.pawmento.pet.domain;

import java.time.LocalDate;

// 반려동물 프로필. Spring·JPA를 모른다. 생성·수정·모드 규칙을 여기서 지킨다
public class Pet {

	private Long id;
	private Long ownerMemberId;
	private String name;
	private String tag;
	private PetSpecies species;
	private String breed;
	private LocalDate birthday;
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
		String trimmedName = requireName(name);
		requireOwner(ownerMemberId);
		requireTag(tag);
		requireSpecies(species);
		requireBirthdayNotFuture(birthday, today);
		return new Pet(ownerMemberId, trimmedName, tag, species, normalizeBreed(breed), birthday, PetMode.ACTIVE);
	}

	// DB에서 꺼낸 값을 도메인으로 되돌릴 때 쓴다. 이미 저장된 값이므로 생성 규칙을 다시 검사하지 않는다
	public static Pet restore(Long id, Long ownerMemberId, String name, String tag, PetSpecies species, String breed,
			LocalDate birthday, PetMode mode, Long version) {
		Pet pet = new Pet(ownerMemberId, name, tag, species, breed, birthday, mode);
		pet.id = id;
		pet.version = version;
		return pet;
	}

	// null인 항목은 바꾸지 않는다. 비운 품종은 빈 문자열로 보낸다
	public void edit(String name, PetSpecies species, String breed, LocalDate birthday, LocalDate today) {
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
			requireBirthdayNotFuture(birthday, today);
			this.birthday = birthday;
		}
	}

	public void changeMode(PetMode mode) {
		if (mode == null) {
			throw new IllegalArgumentException("모드를 선택해 주세요.");
		}
		this.mode = mode;
	}

	public boolean ownedBy(Long memberId) {
		return ownerMemberId.equals(memberId);
	}

	private static void requireOwner(Long ownerMemberId) {
		if (ownerMemberId == null) {
			throw new IllegalArgumentException("소유자가 필요합니다.");
		}
	}

	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("이름은 1~20자로 입력해 주세요.");
		}
		String trimmed = name.strip();
		if (trimmed.isEmpty() || trimmed.length() > 20) {
			throw new IllegalArgumentException("이름은 1~20자로 입력해 주세요.");
		}
		return trimmed;
	}

	private static void requireTag(String tag) {
		if (tag == null || tag.isBlank()) {
			throw new IllegalArgumentException("태그가 필요합니다.");
		}
	}

	private static void requireSpecies(PetSpecies species) {
		if (species == null) {
			throw new IllegalArgumentException("반려동물의 종류를 선택해 주세요.");
		}
	}

	private static void requireBirthdayNotFuture(LocalDate birthday, LocalDate today) {
		if (birthday != null && birthday.isAfter(today)) {
			throw new IllegalArgumentException("생일은 오늘 이전으로 입력해 주세요.");
		}
	}

	private static String normalizeBreed(String breed) {
		if (breed == null || breed.isBlank()) {
			return null;
		}
		return breed.strip();
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerMemberId() {
		return ownerMemberId;
	}

	public String getName() {
		return name;
	}

	public String getTag() {
		return tag;
	}

	public PetSpecies getSpecies() {
		return species;
	}

	public String getBreed() {
		return breed;
	}

	public LocalDate getBirthday() {
		return birthday;
	}

	public PetMode getMode() {
		return mode;
	}

	public Long getVersion() {
		return version;
	}

}
