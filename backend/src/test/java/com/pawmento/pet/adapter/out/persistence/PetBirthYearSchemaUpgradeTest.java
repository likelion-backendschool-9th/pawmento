package com.pawmento.pet.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;
import com.pawmento.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
	"pawmento.media.worker.enabled=false", "pawmento.postcard.worker.enabled=false"})
class PetBirthYearSchemaUpgradeTest {
	@Autowired DataSource dataSource;
	@Autowired JdbcTemplate jdbc;
	private String schema;

	@BeforeEach void legacySchema() {
		schema = "pet178_" + UUID.randomUUID().toString().replace("-", "");
		jdbc.execute("create schema " + schema);
		updateSchema();
		jdbc.execute("alter table " + schema + ".pets drop column birth_year");
		for (int id = 1; id <= 3; id++) {
			String birthday = id == 1 ? "2020-05-12" : id == 2 ? "1899-12-31" : null;
			jdbc.update("insert into " + schema + ".pets (id, owner_member_id, name, tag, species, birthday, mode, version) "
				+ "values (?, 1, 'legacy', ?, 'DOG', cast(? as date), 'ACTIVE', 0)", id, "tag" + id, birthday);
		}
	}
	@AfterEach void cleanup() { jdbc.execute("drop schema if exists " + schema + " cascade"); }

	void updateSchema() {
		var registry = new StandardServiceRegistryBuilder()
			.applySetting(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
			.applySetting(AvailableSettings.DEFAULT_SCHEMA, schema)
			.applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY, CamelCaseToUnderscoresNamingStrategy.class.getName())
			.applySetting(AvailableSettings.HBM2DDL_AUTO, "update").build();
		try {
			new MetadataSources(registry).addAnnotatedClass(PetJpaEntity.class).buildMetadata().buildSessionFactory().close();
		} finally { StandardServiceRegistryBuilder.destroy(registry); }
	}
	void applyConstraint() throws Exception {
		var sql = new ClassPathResource("db/pet-birth-year-schema.sql").getContentAsString(StandardCharsets.UTF_8);
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("set local search_path to " + schema);
				statement.execute(sql);
				connection.commit();
			} catch (Exception e) { connection.rollback(); throw e; }
		}
	}

	@Test @DisplayName("#178-S3 기존 날짜·연도 미상 행을 보존하고 nullable 연도 열과 CHECK를 재적용한다")
	void migrationPreservesRowsAndCanRepeat() throws Exception {
		var before = jdbc.queryForList("select * from " + schema + ".pets order by id");
		updateSchema(); applyConstraint();
		assertThat(jdbc.queryForList("select birth_year from " + schema + ".pets where birth_year is not null")).isEmpty();
		assertThat(jdbc.queryForList("select id, owner_member_id, name, tag, species, breed, birthday, profile_photo_asset_id, mode, version from " + schema + ".pets order by id")).isEqualTo(before);
		jdbc.update("update " + schema + ".pets set birth_year = 2021 where id = 3");
		updateSchema(); applyConstraint();
		assertThat(jdbc.queryForObject("select birth_year from " + schema + ".pets where id = 3", Integer.class)).isEqualTo(2021);
		assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conrelid = ?::regclass and conname = 'ck_pets_birth_information' and convalidated", Integer.class, schema + ".pets")).isEqualTo(1);
	}

	@Test @DisplayName("#178-S3 날짜와 연도 동시 INSERT·UPDATE는 DB에서 거절하고 기존 행을 보존한다")
	void databaseRejectsBothRepresentations() throws Exception {
		updateSchema(); applyConstraint();
		var before = jdbc.queryForList("select * from " + schema + ".pets order by id");
		assertThatThrownBy(() -> jdbc.update("insert into " + schema + ".pets (id, owner_member_id, name, tag, species, birthday, birth_year, mode, version) values (4, 1, 'bad', 'bad', 'DOG', date '2020-01-01', 2020, 'ACTIVE', 0)"))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update " + schema + ".pets set birth_year = 2020 where id = 1"))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(jdbc.queryForList("select * from " + schema + ".pets order by id")).isEqualTo(before);
	}

	@Test @DisplayName("#178-S3 처음 CHECK 적용 때 기존 위반 행도 검사하고 잘못된 이관을 실패시킨다")
	void constraintValidatesExistingRows() {
		updateSchema();
		jdbc.update("update " + schema + ".pets set birth_year = 2020 where id = 1");
		assertThatThrownBy(this::applyConstraint).isInstanceOf(SQLException.class)
			.hasMessageContaining("ck_pets_birth_information");
	}
}
