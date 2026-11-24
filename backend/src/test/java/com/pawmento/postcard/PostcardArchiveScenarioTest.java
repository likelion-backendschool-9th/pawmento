package com.pawmento.postcard;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.postcard.application.port.in.RequestPostcardCommand;
import com.pawmento.postcard.domain.PostcardPolicy;
import java.nio.charset.StandardCharsets;
import com.pawmento.postcard.application.port.in.CleanupPostcardFilesUseCase;
import com.pawmento.postcard.application.port.in.DeletePostcardCommand;
import com.pawmento.postcard.application.port.in.DeletePostcardUseCase;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostcardArchiveScenarioTest extends PostcardScenarioSupport {
  @Autowired CleanupPostcardFilesUseCase cleanup;
  @Autowired DeletePostcardUseCase deletion;
  @Autowired PlatformTransactionManager transactions;

  Path file(String relative, Duration age) throws Exception {
    Path path = ROOT.resolve(relative);
    Files.createDirectories(path.getParent());
    if (!Files.exists(path)) Files.write(path, new byte[] {1});
    Files.setLastModifiedTime(path, FileTime.from(NOW.minus(age)));
    return path;
  }

  @Test
  @DisplayName("#103-S14 정리 작업은 DB와 맞지 않는 오래된 엽서 파일만 지운다")
  void cleansMismatchedFiles() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    long kept = postcard(a, pet, diarySource(diary));
    worker.processNext();
    long expired = postcard(a, pet, diarySource(diary));
    jdbc.update("update postcards set status='FAILED', error_message='만료' where id=?", expired);
    long deleted = postcard(a, pet, diarySource(diary));
    jdbc.update("delete from postcards where id=?", deleted);
    long rolledBack = deleted + 1000, fresh = deleted + 2000;
    var old = Duration.ofHours(2);

    Path keptImage = file("images/" + kept + ".png", old);
    Path expiredImage = file("images/" + expired + ".png", old);
    Path deletedImage = file("images/" + deleted + ".png", old);
    Path rolledBackSource = file("sources/" + rolledBack + ".jpg", old);
    Path freshImage = file("images/" + fresh + ".png", Duration.ofMinutes(10));
    Path outside = ROOT.getParent().resolve("postcard-outside-" + fresh + ".png");
    Files.write(outside, new byte[] {1});
    Files.setLastModifiedTime(outside, FileTime.from(NOW.minus(old)));
    Path otherName = file("images/notes.txt", old);

    cleanup.cleanup();

    assertThat(keptImage).exists();
    assertThat(freshImage).exists();
    assertThat(expiredImage).doesNotExist();
    assertThat(deletedImage).doesNotExist();
    assertThat(rolledBackSource).doesNotExist();
    assertThat(outside).exists();
    assertThat(otherName).exists();
    Files.deleteIfExists(outside);
  }

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

  @Test
  @DisplayName("#103-S8 트랜잭션 안에서 엽서를 지우려 하면 거절하고, 엽서와 이미지·복사 사진 파일이 그대로 남는다")
  void deleteRejectsOuterTransaction() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long done = postcard(a, pet, diarySource(diary(a, pet, "공원")));
    worker.processNext();
    long waiting = postcard(a, pet, diarySource(diary(a, pet, "", asset(a, pet, false))));
    Path image = ROOT.resolve("images/" + done + ".png");
    Path photo = ROOT.resolve("sources/" + waiting + ".jpg");
    assertThat(stateOf(done)).isEqualTo("COMPLETED");
    assertThat(image).exists();
    assertThat(photo).exists();

    var tx = new TransactionTemplate(transactions);
    for (long id : List.of(done, waiting))
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      status -> deletion.delete(new DeletePostcardCommand(a, pet, id))))
          .isInstanceOf(IllegalTransactionStateException.class);

    assertThat(stateOf(done)).isEqualTo("COMPLETED");
    assertThat(stateOf(waiting)).isEqualTo("PROCESSING");
    assertThat(image).exists();
    assertThat(photo).exists();

    // 트랜잭션 밖에서는 행을 커밋한 뒤 파일을 지운다.
    deletion.delete(new DeletePostcardCommand(a, pet, waiting));
    assertThat(rows(pet)).isOne();
    assertThat(photo).doesNotExist();
  }
}
