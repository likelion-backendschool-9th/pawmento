package com.pawmento.pet.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.pawmento.pet.application.exception.PetConcurrentModificationException;
import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.port.out.DeletePetPort;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PetPersistenceAdapter implements SavePetPort, LoadPetPort, DeletePetPort {

	private final PetJpaRepository petJpaRepository;

	@Override
	public Long save(Pet pet) {
		try {
			PetJpaEntity saved = petJpaRepository.save(PetJpaEntity.from(pet));
			// flush해야 @Version 충돌이 커밋 전이 아니라 여기서 난다
			petJpaRepository.flush();
			return saved.getId();
		}
		catch (ObjectOptimisticLockingFailureException e) {
			// 조회 뒤 다른 요청이 먼저 커밋하면 버전 충돌. 완료된 모드·수정이 덮이지 않게 거절한다
			throw new PetConcurrentModificationException();
		}
	}

	@Override
	public Optional<Pet> findById(Long petId) {
		return petJpaRepository.findById(petId).map(PetJpaEntity::toDomain);
	}

	@Override
	public List<Pet> findByOwnerMemberId(Long ownerMemberId) {
		return petJpaRepository.findByOwnerMemberIdOrderByIdAsc(ownerMemberId).stream().map(PetJpaEntity::toDomain)
				.toList();
	}

	@Override
	public boolean existsByOwnerMemberIdAndTag(Long ownerMemberId, String tag) {
		return petJpaRepository.existsByOwnerMemberIdAndTag(ownerMemberId, tag);
	}

	@Override
	public void deleteById(Long petId) {
		try {
			petJpaRepository.deleteById(petId);
			petJpaRepository.flush();
		}
		catch (DataIntegrityViolationException failure) {
			for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
				if (cause instanceof PSQLException sql
						&& sql.getServerErrorMessage() != null
						&& "fk_diary_entries_pet".equals(sql.getServerErrorMessage().getConstraint())
						// PostgreSQL RESTRICT는 23001, 일반 FK 위반은 23503이다.
						&& ("23001".equals(sql.getSQLState()) || "23503".equals(sql.getSQLState()))) {
					throw new PetHasRecordsException(failure);
				}
			}
			throw failure;
		}
	}

}
