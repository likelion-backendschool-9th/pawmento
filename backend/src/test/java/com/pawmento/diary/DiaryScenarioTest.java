package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, DiaryScenarioTest.Time.class})
@AutoConfigureMockMvc
class DiaryScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired CreatePetUseCase pets;
  @Autowired JdbcTemplate jdbc;

  @TestConfiguration
  static class Time {
    @Bean
    @Primary
    Clock diaryClock() {
      return Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneOffset.UTC);
    }
  }

  long pet(long owner) {
    return pets.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
  }

  String url(long pet, long id) {
    return "/api/v1/diaries/" + id + "?petId=" + pet;
  }

  String url(long pet) {
    return "/api/v1/diaries?petId=" + pet;
  }

  ResultActions call(long member, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(
        request.with(jwt().jwt(j -> j.subject(Long.toString(member)).claim("role", "USER"))));
  }

  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  long create(long pet) throws Exception {
    var result =
        call(9001L, json(post(url(pet)), "{\"content\":\"산책\",\"recordedDate\":\"2026-11-17\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.diaryId"))
        .longValue();
  }

  @Test
  @DisplayName("#32-S1 텍스트 CRUD는 서버 작성자·시각을 반환하고 버전을 갱신한다")
  void crud() throws Exception {
    long pet = pet(9001), id = create(pet);
    call(9001, get(url(pet, id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdBy").value(9001))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.createdAt").value("2026-11-17T15:00:00Z"));
    call(9001, json(patch(url(pet, id)), "{\"content\":\"  공원  \",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("공원"))
        .andExpect(jsonPath("$.recordedDate").value("2026-11-17"))
        .andExpect(jsonPath("$.version").value(1));
    call(9001, delete(url(pet, id) + "&version=1"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    call(9001, get(url(pet, id)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("#32-S2 비로그인·비소유자·다른 아이의 일기와 작성자 위조를 거부한다")
  void access() throws Exception {
    long pet = pet(9001), second = pet(9001), id = create(pet);
    mvc.perform(get(url(pet, id))).andExpect(status().isUnauthorized());
    for (var request :
        List.of(
            get(url(pet, id)),
            json(patch(url(pet, id)), "{\"content\":\"x\",\"version\":0}"),
            delete(url(pet, id) + "&version=0")))
      call(9002, request).andExpect(status().isForbidden());
    for (var request :
        List.of(
            get(url(second, id)),
            json(patch(url(second, id)), "{\"content\":\"x\",\"version\":0}"),
            delete(url(second, id) + "&version=0")))
      call(9001, request).andExpect(status().isNotFound());
    call(
            9001,
            json(
                post(url(pet)),
                "{\"content\":\"새"
                    + " 일기\",\"recordedDate\":\"2026-11-18\",\"createdBy\":9002,\"createdAt\":\"2000-01-01T00:00:00Z\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdBy").value(9001))
        .andExpect(jsonPath("$.createdAt").value("2026-11-17T15:00:00Z"));
  }

  @Test
  @DisplayName("#32-S2 접근 가능한 아이의 일기여도 다른 작성자의 수정·삭제는 403이다")
  void originalAuthor() throws Exception {
    long pet = pet(9001), id = create(pet);
    jdbc.update("update diary_entries set created_by=9002 where id=?", id);
    call(9001, json(patch(url(pet, id)), "{\"content\":\"변경\",\"version\":0}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    call(9001, delete(url(pet, id) + "&version=0")).andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject("select content from diary_entries where id=?", String.class, id))
        .isEqualTo("산책");
  }

  @Test
  @DisplayName("#32-S3 잘못된 내용·날짜·형식은 공통 400이며 저장되지 않는다")
  void invalid() throws Exception {
    long pet = pet(9001);
    for (String body :
        List.of(
            "{}",
            "{\"content\":\"  \",\"recordedDate\":\"2026-11-17\"}",
            "{\"content\":\"x\",\"recordedDate\":\"2026-11-19\"}",
            "{\"content\":\"x\",\"recordedDate\":\"2026-02-30\"}",
            "{\"content\":1,\"recordedDate\":\"2026-11-17\"}"))
      call(9001, json(post(url(pet)), body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isZero();
  }

  @Test
  @DisplayName("#32-S4 같은 아이·날짜에 여러 일기를 남길 수 있고 모드 변경 후에도 조회한다")
  void multipleAndMode() throws Exception {
    long pet = pet(9001), first = create(pet), second = create(pet);
    assertThat(first).isNotEqualTo(second);
    call(9001, json(patch("/api/v1/pets/" + pet + "/mode"), "{\"mode\":\"GARDEN\"}"))
        .andExpect(status().isOk());
    call(9001, get(url(pet, first))).andExpect(status().isOk());
  }

  @Test
  @DisplayName("#32-S8 오래된 버전의 수정·삭제는 409이며 원본을 보존한다")
  void staleVersion() throws Exception {
    long pet = pet(9001), id = create(pet);
    call(9001, json(patch(url(pet, id)), "{\"recordedDate\":\"2026-11-16\",\"version\":0}"))
        .andExpect(status().isOk());
    call(9001, json(patch(url(pet, id)), "{\"content\":\"stale\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    call(9001, delete(url(pet, id) + "&version=0")).andExpect(status().isConflict());
    call(9001, get(url(pet, id)))
        .andExpect(jsonPath("$.content").value("산책"))
        .andExpect(jsonPath("$.recordedDate").value("2026-11-16"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  @DisplayName("#32-S20 PATCH의 명시적 null·무변경·잘못된 버전과 필드 형식은 400이다")
  void patchPresence() throws Exception {
    long pet = pet(9001), id = create(pet);
    for (String body :
        List.of(
            "{\"version\":0}",
            "{\"content\":null,\"version\":0}",
            "{\"recordedDate\":null,\"version\":0}",
            "{\"content\":\"x\"}",
            "{\"content\":\"x\",\"version\":-1}",
            "{\"content\":\"x\",\"version\":0.5}",
            "{\"content\":\"x\",\"version\":\"0\"}"))
      call(9001, json(patch(url(pet, id)), body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION"));
  }

  @Test
  @DisplayName("#32-S21 삭제 버전은 헤더가 아닌 쿼리의 음이 아닌 Long 하나여야 한다")
  void deleteQuery() throws Exception {
    long pet = pet(9001), id = create(pet);
    for (String query :
        List.of(
            "",
            "&version=",
            "&version=-1",
            "&version=x",
            "&version=0&version=0",
            "&version=9223372036854775808"))
      call(9001, delete(url(pet, id) + query).header("version", "0"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION"));
    call(9001, get(url(pet, id))).andExpect(status().isOk());
  }

  @Test
  @DisplayName("#32-S22 일기가 있는 아이는 삭제할 수 없고 마지막 일기 삭제 뒤 아이 삭제가 가능하다")
  void petRecords() throws Exception {
    long pet = pet(9001), id = create(pet);
    call(9001, delete("/api/v1/pets/" + pet)).andExpect(status().isConflict());
    call(9001, delete(url(pet, id) + "&version=0")).andExpect(status().isNoContent());
    call(9001, delete("/api/v1/pets/" + pet)).andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from pg_constraint where conname='fk_diary_entries_pet' and not"
                    + " condeferrable and confdeltype='r' and convalidated",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("#32-S23 미연동 위치와 잘못된 첨부 입력을 성공처럼 무시하지 않는다")
  void unsupported() throws Exception {
    long pet = pet(9001), id = create(pet);
    for (String extra :
        List.of(
            "\"location\":{\"mode\":\"MANUAL\",\"latitude\":37.5,\"longitude\":127.0}",
            "\"location\":{\"mode\":\"AUTO\"}",
            "\"location\":{\"mode\":\"MANUAL\"}")) {
      call(
              9001,
              json(
                  post(url(pet)),
                  "{\"content\":\"x\",\"recordedDate\":\"2026-11-17\"," + extra + "}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("CONFLICT"));
      call(9001, json(patch(url(pet, id)), "{\"content\":\"x\",\"version\":0," + extra + "}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
    for (String extra :
        List.of(
            "\"attachmentIds\":null",
            "\"attachmentIds\":{}",
            "\"location\":null",
            "\"location\":{\"mode\":\"BAD\"}"))
      call(
              9001,
              json(
                  post(url(pet)),
                  "{\"content\":\"x\",\"recordedDate\":\"2026-11-17\"," + extra + "}"))
          .andExpect(status().isBadRequest());
    call(
            9001,
            json(
                post(url(pet)),
                "{\"content\":\"x\",\"recordedDate\":\"2026-11-17\",\"attachmentIds\":[],\"location\":{\"mode\":\"NONE\"}}"))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("#32-S27 모든 CRUD는 양의 petId 쿼리 하나만 받고 헤더·본문으로 대체하지 않는다")
  void rootPetQuery() throws Exception {
    long pet = pet(9001), id = create(pet);
    for (String query :
        List.of(
            "",
            "?petId=",
            "?petId=0",
            "?petId=abc",
            "?petId=1&%70etId=2",
            "?petId=9223372036854775808")) {
      for (var request :
          List.of(
              json(
                  post(java.net.URI.create("/api/v1/diaries" + query)),
                  "{\"content\":\"일기\",\"recordedDate\":\"2026-11-17\",\"petId\":" + pet + "}"),
              get(java.net.URI.create("/api/v1/diaries/" + id + query)),
              json(
                  patch(java.net.URI.create("/api/v1/diaries/" + id + query)),
                  "{\"content\":\"변경\",\"version\":0}"),
              delete(
                  java.net.URI.create(
                      "/api/v1/diaries/"
                          + id
                          + query
                          + (query.isEmpty() ? "?" : "&")
                          + "version=0"))))
        call(9001, request.header("petId", pet))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION"));
    }
    call(9001, get(url(pet, id)).header("petId", pet + 1)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("#32-S27 기존 중첩 CRUD 경로는 호환 별칭 없이 404다")
  void retiredNestedPaths() throws Exception {
    long pet = pet(9001), id = create(pet);
    String old = "/api/v1/pets/" + pet + "/diaries";
    for (var request :
        List.of(
            json(post(old), "{\"content\":\"일기\",\"recordedDate\":\"2026-11-17\"}"),
            get(old),
            get(old + "/calendar?month=2026-11"),
            get(old + "/" + id),
            json(patch(old + "/" + id), "{\"content\":\"변경\",\"version\":0}"),
            delete(old + "/" + id + "?version=0")))
      call(9001, request).andExpect(status().isNotFound());
  }
}
