package com.pawmento.postcard;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.postcard.application.port.in.RequestPostcardCommand;
import com.pawmento.postcard.domain.PostcardPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

class PostcardArchiveScenarioTest extends PostcardScenarioSupport {
  @Test
  @DisplayName("#103-S8 엽서를 최신순으로 나눠 보고, 완성 엽서만 이미지를 열고, 지우면 파일도 사라진다")
  void listOpenDelete() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    doReturn(new PostcardPolicy(true, 30, 100)).when(policies).load();
    var ids = new ArrayList<Long>();
    for (int i = 0; i < 25; i++)
      ids.add(
          requestUseCase.request(new RequestPostcardCommand(a, pet, "DIARY", diary, null, true)).postcardId());
    for (int i = 0; i < 5; i++) worker.processNext();
    for (long id : ids.subList(5, 10))
      jdbc.update("update postcards set status='FAILED', error_message='실패' where id=?", id);
    var newest = new ArrayList<>(ids);
    Collections.reverse(newest);

    String first =
        call(a, get("/api/v1/postcards?petId=" + pet + "&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(20)))
            .andExpect(jsonPath("$.items[0].postcardId").value(newest.get(0)))
            .andExpect(jsonPath("$.items[19].postcardId").value(newest.get(19)))
            .andExpect(jsonPath("$.items[0].status").value("PROCESSING"))
            .andExpect(jsonPath("$.nextCursor").value(newest.get(19)))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(first).contains("\"status\":\"FAILED\"");
    call(a, get("/api/v1/postcards?petId=" + pet + "&size=20&cursor=" + newest.get(19)))
        .andExpect(jsonPath("$.items", hasSize(5)))
        .andExpect(jsonPath("$.items[*].status", everyItem(is("COMPLETED"))))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()));

    for (long id : List.of(ids.get(5), ids.get(24)))
      call(a, get("/api/v1/postcards/" + id + "/image?petId=" + pet)).andExpect(status().isNotFound());

    long done = ids.get(0);
    assertThat(Files.exists(ROOT.resolve("images/" + done + ".png"))).isTrue();
    call(a, get("/api/v1/postcards/" + done + "/image?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"));
    call(a, delete("/api/v1/postcards/" + done + "?petId=" + pet)).andExpect(status().isNoContent());
    call(a, get("/api/v1/postcards/" + done + "?petId=" + pet)).andExpect(status().isNotFound());
    call(a, get("/api/v1/postcards/" + done + "/image?petId=" + pet)).andExpect(status().isNotFound());
    call(a, get("/api/v1/postcards?petId=" + pet + "&size=50"))
        .andExpect(jsonPath("$.items", hasSize(24)))
        .andExpect(jsonPath("$.items[*].postcardId", not(hasItem((int) done))));
    assertThat(Files.exists(ROOT.resolve("images/" + done + ".png"))).isFalse();
  }

  @Test
  @DisplayName("#103-S9 남의 반려동물은 403, 다른 반려동물의 엽서는 404, 로그인 없이는 401이다")
  void permissions() throws Exception {
    long a = member(), b = member();
    long pet = pet(a, "GARDEN"), bPet = pet(b, "GARDEN");
    long id = postcard(a, pet, diarySource(diary(a, pet, "공원")));
    worker.processNext();
    assertThat(stateOf(id)).isEqualTo("COMPLETED");

    String detail = "/api/v1/postcards/" + id;
    for (var req :
        List.of(
            get(detail + "?petId=" + pet),
            get(detail + "/image?petId=" + pet),
            delete(detail + "?petId=" + pet)))
      call(b, req).andExpect(status().isForbidden());
    for (var req :
        List.of(
            get(detail + "?petId=" + bPet),
            get(detail + "/image?petId=" + bPet),
            delete(detail + "?petId=" + bPet)))
      call(b, req).andExpect(status().isNotFound());
    assertThat(stateOf(id)).isEqualTo("COMPLETED");

    for (var req :
        List.of(
            get(detail + "?petId=" + pet),
            get(detail + "/image?petId=" + pet),
            delete(detail + "?petId=" + pet),
            get("/api/v1/postcards?petId=" + pet),
            get("/api/v1/postcards/usage?petId=" + pet),
            post("/api/v1/postcards?petId=" + pet)
                .contentType(MediaType.APPLICATION_JSON)
                .content(diarySource(1))))
      mvc.perform(req).andExpect(status().isUnauthorized());
    assertThat(stateOf(id)).isEqualTo("COMPLETED");
  }
}
