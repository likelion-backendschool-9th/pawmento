package com.pawmento.diary.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.exception.DiaryVersionConflictException;
import com.pawmento.diary.domain.DiaryEntry;
import com.pawmento.diary.domain.DiaryLocationIntent;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import(TestcontainersConfiguration.class)
@Transactional
class DiaryLocationIntentPersistenceTest {
  @Autowired DiaryEntryPersistenceAdapter adapter;
  @Autowired CreatePetUseCase pets;
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  private final Instant now = Instant.parse("2026-11-17T15:00:00Z");

  private DiaryEntry create() {
    long pet = pets.create(new CreatePetCommand(9001L, "보리", "DOG", null, null)).petId();
    return adapter.save(DiaryEntry.create(pet, 9001L, "산책", LocalDate.of(2026, 11, 17), now));
  }

  private DiaryEntry reload(long id) {
    em.clear();
    return adapter.findById(id).orElseThrow();
  }

  @Test
  @DisplayName("#44-S4 위치 원본은 일기 버전 갱신과 함께 저장되고 본문 수정에도 유지된다")
  void persistAndPreserve() {
    var entry = create();
    assertThat(entry.getLocationIntent()).isEqualTo(DiaryLocationIntent.none());
    var intent = DiaryLocationIntent.manual(-37.1234567890123, -73.1234567890123, " 공원 ");
    entry.changeLocation(9001L, intent, now.plusSeconds(1));
    var saved = adapter.save(entry);
    assertThat(saved.getVersion()).isEqualTo(1);
    var loaded = reload(saved.getId());
    assertThat(loaded.getLocationIntent()).isEqualTo(intent);
    loaded.edit(9001L, "새 본문", null, now.plusSeconds(2));
    var edited = adapter.save(loaded);
    assertThat(reload(edited.getId()).getLocationIntent()).isEqualTo(intent);
    assertThat(edited.getVersion()).isEqualTo(2);
    assertThatThrownBy(() -> adapter.save(saved)).isInstanceOf(DiaryVersionConflictException.class);
    assertThat(reload(edited.getId()).getContent()).isEqualTo("새 본문");
    var current = reload(edited.getId());
    current.changeLocation(9001L, DiaryLocationIntent.none(), now.plusSeconds(3));
    var cleared = adapter.save(current);
    assertThat(cleared.getVersion()).isEqualTo(3);
    assertThat(reload(cleared.getId()).getLocationIntent()).isEqualTo(DiaryLocationIntent.none());
    assertThat(
            jdbc.queryForMap(
                    "select location_latitude,location_longitude,location_place_name from"
                        + " diary_entries where id=?",
                    cleared.getId())
                .values())
        .containsOnlyNulls();
  }

  @Test
  @DisplayName("#44-S13 기존 일기의 미이관 상태와 기존 핀은 스키마 재적용·본문 수정 후에도 유지된다")
  void preservesLegacyAndRepeat() throws Exception {
    var entry = create();
    jdbc.update("update diary_entries set location_mode=null where id=?", entry.getId());
    jdbc.update(
        "insert into"
            + " diary_location(diary_id,pet_id,created_by,latitude,longitude,place_name,created_at,updated_at)"
            + " values(?,?,?,?,?,?,?,?)",
        entry.getId(),
        entry.getPetId(),
        9001L,
        37.5,
        127.5,
        "기존 공원",
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));
    var pin = jdbc.queryForMap("select * from diary_location where diary_id=?", entry.getId());
    String sql =
        new ClassPathResource("db/diary-schema.sql").getContentAsString(StandardCharsets.UTF_8);
    jdbc.execute(sql);
    jdbc.execute(sql);
    var loaded = reload(entry.getId());
    assertThat(loaded.getLocationIntent()).isNull();
    loaded.edit(9001L, "본문 수정", null, now.plusSeconds(1));
    adapter.save(loaded);
    assertThat(reload(entry.getId()).getLocationIntent()).isNull();
    assertThat(jdbc.queryForMap("select * from diary_location where diary_id=?", entry.getId()))
        .isEqualTo(pin);
    assertThat(
            jdbc.queryForObject(
                "select data_type from information_schema.columns where table_name='diary_entries'"
                    + " and column_name='location_latitude'",
                String.class))
        .isEqualTo("double precision");
  }

  @Test
  @DisplayName("#44-S13 double precision 좌표 왕복은 정상 값을 반올림하거나 불일치로 바꾸지 않는다")
  void roundTripsExactCoordinates() {
    var entry = create();
    for (double coordinate : new double[] {-0.0, 0, Math.nextUp(37.0), -89.99999999999999, 90}) {
      entry.changeLocation(
          9001L, DiaryLocationIntent.manual(coordinate, -179.99999999999997, null), now);
      var saved = adapter.save(entry);
      entry = reload(saved.getId());
      assertThat(Double.doubleToLongBits(entry.getLocationIntent().latitude()))
          .isEqualTo(Double.doubleToLongBits(coordinate == 0 ? 0 : coordinate));
      assertThat(Double.doubleToLongBits(entry.getLocationIntent().longitude()))
          .isEqualTo(Double.doubleToLongBits(-179.99999999999997));
    }
  }

  @Test
  @DisplayName("#44-S13 저장된 위치 원본 이상은 사용자 입력 오류로 재포장하지 않는다")
  void corruptLocationIsServerError() {
    var entry = create();
    jdbc.execute("alter table diary_entries drop constraint ck_diary_entries_location_intent");
    jdbc.update(
        "update diary_entries set"
            + " location_mode='MANUAL',location_latitude=91,location_longitude=127 where id=?",
        entry.getId());
    assertThatThrownBy(() -> reload(entry.getId()))
        .isInstanceOf(
            com.pawmento.diary.application.exception.DiaryLocationDataInvalidException.class)
        .hasCauseInstanceOf(
            com.pawmento.diary.domain.exception.InvalidDiaryLocationIntentException.class);
  }

  @Test
  @DisplayName("#44-S13 기존의 정상 핀만 미이관 원본으로 백필하며 revision을 맞추고 재적용으로 사용자 값을 덮지 않는다")
  void backfillAndRepeat() {
    var entry = create();
    jdbc.update("update diary_entries set location_mode=null,version=5 where id=?", entry.getId());
    jdbc.update(
        "insert into"
            + " diary_location(diary_id,pet_id,created_by,latitude,longitude,place_name,created_at,updated_at)"
            + " values(?,?,?,?,?,?,?,?)",
        entry.getId(),
        entry.getPetId(),
        9001L,
        -0.0,
        127.123456789,
        "공원",
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));
    var before =
        jdbc.queryForMap(
            "select created_at,updated_at,id from diary_location where diary_id=?", entry.getId());
    DiaryLocationBackfill.run(jdbc);
    DiaryLocationBackfill.run(jdbc);
    var loaded = reload(entry.getId());
    assertThat(loaded.getLocationIntent())
        .isEqualTo(DiaryLocationIntent.manual(0.0, 127.123456789, "공원"));
    assertThat(loaded.getVersion()).isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "select source_revision from diary_location where diary_id=?",
                Long.class,
                entry.getId()))
        .isEqualTo(5L);
    assertThat(
            jdbc.queryForMap(
                "select created_at,updated_at,id from diary_location where diary_id=?",
                entry.getId()))
        .isEqualTo(before);
    loaded.changeLocation(9001L, DiaryLocationIntent.none(), now.plusSeconds(1));
    adapter.save(loaded);
    DiaryLocationBackfill.run(jdbc);
    assertThat(reload(entry.getId()).getLocationIntent()).isEqualTo(DiaryLocationIntent.none());
  }

  @Test
  @DisplayName("#44-S13 초기 이관의 아이·작성자 충돌은 자동 재귀속이나 다른 행의 부분 백필 없이 중단한다")
  void refusesInitialConflict() {
    var first = create();
    var second = create();
    jdbc.update(
        "update diary_entries set location_mode=null where id in (?,?)",
        first.getId(),
        second.getId());
    jdbc.update(
        "insert into"
            + " diary_location(diary_id,pet_id,created_by,latitude,longitude,created_at,updated_at)"
            + " values(?,?,9002,1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        second.getId(),
        second.getPetId());
    var pin = jdbc.queryForMap("select * from diary_location where diary_id=?", second.getId());
    assertThatThrownBy(() -> DiaryLocationBackfill.run(jdbc))
        .isInstanceOf(
            com.pawmento.diary.application.exception.DiaryLocationDataInvalidException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where id in (?,?) and location_mode is null",
                Long.class,
                first.getId(),
                second.getId()))
        .isEqualTo(2L);
    assertThat(jdbc.queryForMap("select * from diary_location where diary_id=?", second.getId()))
        .isEqualTo(pin);
  }

  @Test
  @DisplayName("#44-S13 부모 없는 기존 지도 위치는 보존하고 초기 이관을 중단한다")
  void refusesOrphanPin() {
    long id = 9000000 + create().getId();
    jdbc.update(
        "insert into"
            + " diary_location(diary_id,pet_id,created_by,latitude,longitude,created_at,updated_at)"
            + " values(?,1,9001,1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id);
    var before = jdbc.queryForMap("select * from diary_location where diary_id=?", id);
    assertThatThrownBy(() -> DiaryLocationBackfill.run(jdbc))
        .isInstanceOf(
            com.pawmento.diary.application.exception.DiaryLocationDataInvalidException.class);
    assertThat(jdbc.queryForMap("select * from diary_location where diary_id=?", id))
        .isEqualTo(before);
  }
}
