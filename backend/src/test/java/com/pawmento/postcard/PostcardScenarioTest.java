package com.pawmento.postcard;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
@Import({TestcontainersConfiguration.class, PostcardScenarioTest.Time.class})
@AutoConfigureMockMvc
class PostcardScenarioTest {
  static final long A = 7101L;
  static final long B = 7102L;

  @TestConfiguration(proxyBeanMethods = false)
  static class Time {
    @Bean
    @Primary
    Clock postcardClock() {
      return Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @Autowired CreateDiaryEntryUseCase diaries;

  long pet(long owner, String mode) {
    long id = pets.create(new CreatePetCommand(owner, "콩이", "DOG", "말티즈", null)).petId();
    if (!"ACTIVE".equals(mode)) modes.changeMode(new ChangePetModeCommand(owner, id, mode));
    return id;
  }

  long diary(long owner, long pet, String content) {
    return diaries
        .create(new CreateDiaryEntryCommand(owner, pet, content, LocalDate.of(2026, 11, 20)))
        .diaryId();
  }

  ResultActions call(long member, MockHttpServletRequestBuilder req) throws Exception {
    return mvc.perform(req.with(jwt().jwt(j -> j.subject("" + member))));
  }

  ResultActions request(long member, long pet, String body) throws Exception {
    return call(
        member,
        post("/api/v1/postcards?petId=" + pet).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  static String diarySource(long diary) {
    return "{\"source\":{\"kind\":\"DIARY\",\"id\":" + diary + "},\"consent\":true}";
  }

  long rows(long pet) {
    return jdbc.queryForObject("select count(*) from postcards where pet_id=?", Long.class, pet);
  }

  @Test
  @DisplayName("#103-S1 정원 모드 일기로 엽서를 요청하면 PROCESSING으로 저장되고 상세에서 보인다")
  void requestFromDiary() throws Exception {
    long pet = pet(A, "GARDEN");
    long diary = diary(A, pet, "바닷가에서 노을을 보며 산책했다");

    var created =
        request(A, pet, diarySource(diary))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.postcardId").isNumber())
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andReturn();
    long id =
        Long.parseLong(
            created.getResponse().getContentAsString().replaceAll(".*\"postcardId\":(\\d+).*", "$1"));

    call(A, get("/api/v1/postcards/" + id + "?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PROCESSING"))
        .andExpect(jsonPath("$.source.kind").value("DIARY"))
        .andExpect(jsonPath("$.source.id").value(diary))
        .andExpect(jsonPath("$.imageUrl").value(nullValue()))
        .andExpect(jsonPath("$.title").value("콩이와 함께한 작은 순간"))
        .andExpect(jsonPath("$.text").value("바닷가에서 노을을 보며 산책했다"));
  }
}
