package com.pawmento.diary.adapter.out.persistence;

import java.time.*;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DiaryEntryJpaRepository extends JpaRepository<DiaryEntryJpaEntity, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from DiaryEntryJpaEntity e where e.id=:id")
  java.util.Optional<DiaryEntryJpaEntity> findForUpdate(@Param("id") Long id);

  // 날짜 조건을 페이지와 count에서 동일하게 사용한다. native OFFSET은 int 범위를 넘는 유효 페이지도 처리한다.
  String FILTER =
      " where pet_id = :petId and (cast(:fromDate as date) is null or recorded_date >= :fromDate)"
          + " and (cast(:toDate as date) is null or recorded_date <= :toDate)";

  @Query(
      value =
          "select * from diary_entries"
              + FILTER
              + " order by recorded_date desc, id desc limit :pageSize offset :offsetRows",
      nativeQuery = true)
  List<DiaryEntryJpaEntity> findPage(
      @Param("petId") Long petId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to,
      @Param("pageSize") int size,
      @Param("offsetRows") long offset);

  @Query(value = "select count(*) from diary_entries" + FILTER, nativeQuery = true)
  long countEntries(
      @Param("petId") Long petId, @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);

  interface DayTotal {
    LocalDate getDate();

    long getTotal();
  }

  @Query(
      "select e.recordedDate as date, count(e) as total from DiaryEntryJpaEntity e where e.petId ="
          + " :petId and e.recordedDate >= :start and e.recordedDate < :before group by"
          + " e.recordedDate order by e.recordedDate")
  List<DayTotal> countDays(
      @Param("petId") Long petId,
      @Param("start") LocalDate start,
      @Param("before") LocalDate before);

  boolean existsByPetId(Long petId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update DiaryEntryJpaEntity e set e.title = :title, e.content = :content, e.recordedDate ="
          + " :date, e.updatedAt = :now, e.locationMode=:locationMode,"
          + " e.locationLatitude=:latitude, e.locationLongitude=:longitude,"
          + " e.locationPlaceName=:placeName, e.version = e.version + 1 where e.id = :id and"
          + " e.version = :version")
  int updateVersion(
      @Param("id") Long id,
      @Param("version") Long version,
      @Param("content") String content,
      @Param("title") String title,
      @Param("date") LocalDate date,
      @Param("now") Instant now,
      @Param("locationMode") com.pawmento.diary.domain.DiaryLocationIntent.Mode locationMode,
      @Param("latitude") Double latitude,
      @Param("longitude") Double longitude,
      @Param("placeName") String placeName);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from DiaryEntryJpaEntity e where e.id = :id and e.version = :version")
  int deleteVersion(@Param("id") Long id, @Param("version") Long version);
}
