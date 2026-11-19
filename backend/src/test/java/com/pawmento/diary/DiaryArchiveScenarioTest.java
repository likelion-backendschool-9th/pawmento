package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.pet.application.port.in.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, DiaryScenarioTest.Time.class})
@AutoConfigureMockMvc
class DiaryArchiveScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired CreatePetUseCase pets;
  @Autowired JdbcTemplate jdbc;

  long pet() {
    return pets.create(new CreatePetCommand(9001L, "보리", "DOG", null, null)).petId();
  }

  long seed(long pet, long author, String date) {
    return jdbc.queryForObject(
        "insert into"
            + " diary_entries(pet_id,created_by,content,recorded_date,created_at,updated_at,version)"
            + " values(?,?,?,?,'2026-11-17T15:00:00Z','2026-11-17T15:00:00Z',0) returning id",
        Long.class,
        pet,
        author,
        "일기 " + date,
        LocalDate.parse(date));
  }

  ResultActions read(String path) throws Exception {
    return mvc.perform(get(URI.create(path)).with(jwt().jwt(j -> j.subject("9001"))));
  }

  String list(long pet) {
    return "/api/v1/diaries?petId=" + pet;
  }

  String calendar(long pet, String month) {
    return "/api/v1/diaries/calendar?petId=" + pet + "&month=" + month;
  }

  @Test
  @DisplayName("#32-S24 같은 아이의 여러 작성자 일기를 모으고 날짜·ID 역순으로 페이지 조회한다")
  void authorsAndPages() throws Exception {
    long pet = pet(), other = pet();
    long first = seed(pet, 9001, "2026-11-15"),
        second = seed(pet, 9002, "2026-11-17"),
        third = seed(pet, 9001, "2026-11-17");
    seed(other, 9002, "2026-11-17");
    read(list(pet) + "&size=2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].recordId").value(third))
        .andExpect(jsonPath("$.items[1].recordId").value(second))
        .andExpect(jsonPath("$.items[1].createdBy").value(9002))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
    read(list(pet) + "&page=1&size=2")
        .andExpect(jsonPath("$.items[0].recordId").value(first))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(3));
    for (String query : List.of("&page=2&size=2", "&page=2147483647&size=100"))
      read(list(pet) + query)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items").isEmpty())
          .andExpect(jsonPath("$.totalElements").value(3));
    read(list(pet))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.items.length()").value(3));
    mvc.perform(
            patch("/api/v1/pets/" + pet + "/mode")
                .with(jwt().jwt(j -> j.subject("9001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"GARDEN\"}"))
        .andExpect(status().isOk());
    read(list(pet)).andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @DisplayName("#32-S7 사건 날짜·양쪽 및 한쪽 기간은 포함 경계로 조회하고 미래의 빈 결과도 허용한다")
  void dates() throws Exception {
    long pet = pet();
    for (String date : List.of("2024-02-28", "2024-02-29", "2024-03-01", "2024-03-02"))
      seed(pet, 9001, date);
    read(list(pet) + "&date=2024-02-29").andExpect(jsonPath("$.totalElements").value(1));
    read(list(pet) + "&from=2024-02-29&to=2024-03-01")
        .andExpect(jsonPath("$.totalElements").value(2));
    read(list(pet) + "&from=2024-03-01").andExpect(jsonPath("$.totalElements").value(2));
    read(list(pet) + "&to=2024-02-29").andExpect(jsonPath("$.totalElements").value(2));
    read(list(pet) + "&date=2030-01-01")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  @DisplayName("#32-S6 달력은 윤년·월 경계의 사건 날짜로 집계하고 수정·삭제가 다음 조회에 반영된다")
  void calendarCounts() throws Exception {
    long pet = pet(), other = pet();
    seed(pet, 9001, "2024-02-28");
    long changed = seed(pet, 9002, "2024-02-29"), removed = seed(pet, 9001, "2024-02-29");
    seed(pet, 9001, "2024-03-01");
    seed(other, 9002, "2024-02-29");
    read(calendar(pet, "2024-02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.petId").value(pet))
        .andExpect(jsonPath("$.month").value("2024-02"))
        .andExpect(jsonPath("$.days.length()").value(2))
        .andExpect(jsonPath("$.days[0].date").value("2024-02-28"))
        .andExpect(jsonPath("$.days[1].date").value("2024-02-29"))
        .andExpect(jsonPath("$.days[1].count").value(2));
    read(list(pet) + "&date=2024-02-29").andExpect(jsonPath("$.totalElements").value(2));
    read(calendar(pet, "2024-04")).andExpect(jsonPath("$.days").isEmpty());
    // 기존 작성자의 수정 권한을 유지하면서 집계 변화를 확인한다.
    jdbc.update("update diary_entries set created_by=9001 where id=?", changed);
    mvc.perform(
            patch("/api/v1/diaries/" + changed + "?petId=" + pet)
                .with(jwt().jwt(j -> j.subject("9001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordedDate\":\"2024-03-10\",\"version\":0}"))
        .andExpect(status().isOk());
    read(calendar(pet, "2024-02")).andExpect(jsonPath("$.days[1].count").value(1));
    mvc.perform(
            delete("/api/v1/diaries/" + removed + "?petId=" + pet + "&version=0")
                .with(jwt().jwt(j -> j.subject("9001"))))
        .andExpect(status().isNoContent());
    read(calendar(pet, "2024-02")).andExpect(jsonPath("$.days.length()").value(1));
    read(calendar(pet, "2024-03"))
        .andExpect(jsonPath("$.days[0].date").value("2024-03-01"))
        .andExpect(jsonPath("$.days[1].date").value("2024-03-10"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from pg_indexes where tablename='diary_entries' and"
                    + " indexname='ix_diary_entries_pet_date_id'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("#32-S5 빈 결과와 비로그인·비소유자·없는 아이를 구분하고 다른 아이의 일기를 노출하지 않는다")
  void access() throws Exception {
    long pet = pet();
    seed(pet, 9001, "2026-11-17");
    for (String path : List.of(list(pet), calendar(pet, "2026-11"))) {
      mvc.perform(get(URI.create(path))).andExpect(status().isUnauthorized());
      mvc.perform(get(URI.create(path)).with(jwt().jwt(j -> j.subject("9002"))))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    read(list(Long.MAX_VALUE)).andExpect(status().isNotFound());
    read(calendar(Long.MAX_VALUE, "2026-11")).andExpect(status().isNotFound());
    long empty = pet();
    read(list(empty)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    read(calendar(empty, "2026-11"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days").isEmpty());
  }

  @Test
  @DisplayName("#32-S25 쿼리의 중복·빈 값·인코딩·날짜·페이지 오류는 공통 400이다")
  void malformedQueries() throws Exception {
    long pet = pet();
    String prefix = "petId=" + pet;
    for (String query :
        List.of(
            prefix + "&page=1&page=2",
            prefix + "&size=",
            prefix + "&size=0",
            prefix + "&size=101",
            prefix + "&page=-1",
            prefix + "&page=0.5",
            prefix + "&page=2147483648",
            prefix + "&page=abc",
            prefix + "&date=2023-02-29",
            prefix + "&date=2024-2-29",
            prefix + "&date=2024-02-29&from=2024-02-01",
            prefix + "&from=2024-03-01&to=2024-02-28",
            prefix + "&from=",
            prefix + "&%70etId=" + pet,
            "petId=%XX",
            "petId=%FF",
            prefix + "&%XX=x"))
      mvc.perform(
              get("/api/v1/diaries")
                  .with(jwt().jwt(j -> j.subject("9001")))
                  .with(
                      r -> {
                        r.setQueryString(query);
                        return r;
                      }))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION"));
    for (String query :
        List.of(
            prefix,
            prefix + "&month=",
            prefix + "&month=2026-11&month=2026-12",
            prefix + "&month=2026-13",
            prefix + "&month=2026-1",
            prefix + "&month=2026-11&%70etId=" + pet))
      mvc.perform(
              get("/api/v1/diaries/calendar")
                  .with(jwt().jwt(j -> j.subject("9001")))
                  .with(
                      r -> {
                        r.setQueryString(query);
                        return r;
                      }))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION"));
  }

  @Test
  @DisplayName("#32-S25 필수 쿼리는 헤더·본문·파라미터 바인딩으로 채우지 않고 기본값은 생략에만 적용한다")
  void queryOnly() throws Exception {
    long pet = pet();
    mvc.perform(
            get("/api/v1/diaries")
                .with(jwt().jwt(j -> j.subject("9001")))
                .header("petId", pet)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"petId\":" + pet + "}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get(URI.create("/api/v1/diaries/calendar?petId=" + pet))
                .with(jwt().jwt(j -> j.subject("9001")))
                .header("month", "2026-11")
                .param("month", "2026-11")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2026-11\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/diaries/calendar?month=2026-11")
                .with(jwt().jwt(j -> j.subject("9001")))
                .header("petId", pet))
        .andExpect(status().isBadRequest());
    read(list(pet) + "&size=100")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }
}
