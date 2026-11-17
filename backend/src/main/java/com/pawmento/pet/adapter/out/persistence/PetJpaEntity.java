package com.pawmento.pet.adapter.out.persistence;

import java.time.LocalDate;

import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetMode;
import com.pawmento.pet.domain.PetSpecies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "pets", uniqueConstraints = @UniqueConstraint(name = "uk_pets_owner_tag",
		columnNames = { "owner_member_id", "tag" }))
public class PetJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_member_id", nullable = false)
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PetMode mode;

	protected PetJpaEntity() {
	}

	public static PetJpaEntity from(Pet pet) {
		PetJpaEntity entity = new PetJpaEntity();
		entity.id = pet.getId();
		entity.ownerMemberId = pet.getOwnerMemberId();
		entity.name = pet.getName();
		entity.tag = pet.getTag();
		entity.species = pet.getSpecies();
		entity.breed = pet.getBreed();
		entity.birthday = pet.getBirthday();
		entity.mode = pet.getMode();
		return entity;
	}

	public Pet toDomain() {
		return Pet.restore(id, ownerMemberId, name, tag, species, breed, birthday, mode);
	}

	public Long getId() {
		return id;
	}

}
