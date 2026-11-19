package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.pawmento.map.application.exception.DiaryLocationAlreadyExistsException;
import com.pawmento.map.domain.DiaryLocation;

// DB 없이 어댑터가 무결성 오류를 가려서 변환하는지만 확인한다. 진짜 유일 제약 위반은 저장소 테스트(uniquePerRecord)가 맡는다
class DiaryLocationPersistenceAdapterConstraintTest {

	private final DiaryLocationJpaRepository repository = mock(DiaryLocationJpaRepository.class);

	private final DiaryLocationPersistenceAdapter adapter = new DiaryLocationPersistenceAdapter(repository);

	private DiaryLocation newLocation() {
		return DiaryLocation.create(100L, 10L, 1L, 37.0, 127.0, null);
	}

	private DataIntegrityViolationException violationOf(String constraintName) {
		return new DataIntegrityViolationException("무결성 오류",
				new ConstraintViolationException("제약 위반", new SQLException("원인"), constraintName));
	}

	@Test
	@DisplayName("#19-S1 기록당 위치 하나라는 유일 제약을 어기면 이미 있는 위치 오류로 바꾼다")
	void convertsRecordUniqueViolation() {
		when(repository.save(any())).thenThrow(violationOf(DiaryLocationJpaEntity.DIARY_UNIQUE_CONSTRAINT));

		assertThatThrownBy(() -> adapter.save(newLocation())).isInstanceOf(DiaryLocationAlreadyExistsException.class);
	}

	@Test
	@DisplayName("#19-S1 유일 제약 이름은 대소문자를 가리지 않고 비교한다")
	void constraintNameIsCaseInsensitive() {
		when(repository.save(any()))
			.thenThrow(violationOf(DiaryLocationJpaEntity.DIARY_UNIQUE_CONSTRAINT.toUpperCase()));

		assertThatThrownBy(() -> adapter.save(newLocation())).isInstanceOf(DiaryLocationAlreadyExistsException.class);
	}

	@Test
	@DisplayName("#19-S1 다른 제약을 어긴 무결성 오류는 이미 있는 위치로 바꾸지 않고 그대로 던진다")
	void keepsOtherConstraintViolation() {
		DataIntegrityViolationException other = violationOf("some_other_constraint");
		when(repository.save(any())).thenThrow(other);

		assertThatThrownBy(() -> adapter.save(newLocation())).isSameAs(other);
	}

	@Test
	@DisplayName("#19-S1 제약 정보 없이 온 무결성 오류도 그대로 던진다")
	void keepsViolationWithoutConstraintInfo() {
		DataIntegrityViolationException plain = new DataIntegrityViolationException("원인 불명");
		when(repository.save(any())).thenThrow(plain);

		assertThatThrownBy(() -> adapter.save(newLocation())).isSameAs(plain);
		assertThat(plain).isNotInstanceOf(DiaryLocationAlreadyExistsException.class);
	}

}
