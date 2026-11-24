package com.pawmento.postcard;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.postcard.application.exception.PostcardGenerationFailedException;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.application.service.PostcardWorkTransactions;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PostcardWorkScenarioTest extends PostcardScenarioSupport {
  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

  @Autowired DeleteDiaryEntryUseCase deleteDiaries;
  @Autowired EditPetUseCase editPets;
  @Autowired LoadPostcardPort postcards;
  @Autowired PostcardWorkTransactions transactions;

  // 서버가 외부 호출 도중 꺼진 것을 흉내 낸다. 작업 코드가 잡지 않는 Error다.
  static final class Crash extends Error {}

  void returnsPng() {
    doReturn(new GeneratedPostcardImage(PNG, "image/png")).when(generator).generate(any());
  }

  List<PostcardImageRequest> generated() {
    var captor = ArgumentCaptor.forClass(PostcardImageRequest.class);
    verify(generator, atLeast(0)).generate(captor.capture());
    return captor.getAllValues();
  }

  void drain() {
    while (worker.processNext()) {}
  }

  @Test
  @DisplayName("#103-S4 생성 작업이 요청 때 복사한 내용으로 그림을 받아 COMPLETED로 바꾼다")
  void completes() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long diary = diary(a, pet, "바닷가에서 노을을 보며 산책했다");
    long id = postcard(a, pet, diarySource(diary));
    deleteDiaries.delete(new DeleteDiaryEntryCommand(a, pet, diary, 0L));
    returnsPng();

    assertThat(worker.processNext()).isTrue();

    var sent = generated();
    assertThat(sent).hasSize(1);
    assertThat(sent.get(0).content()).isEqualTo("바닷가에서 노을을 보며 산책했다");
    assertThat(sent.get(0).species()).isEqualTo("DOG");
    assertThat(sent.get(0).breed()).isEqualTo("말티즈");
    call(a, get("/api/v1/postcards/" + id + "?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.title").value("콩이와 함께한 작은 순간"))
        .andExpect(jsonPath("$.text").value("바닷가에서 노을을 보며 산책했다"))
        .andExpect(jsonPath("$.imageUrl").value("/api/v1/postcards/" + id + "/image?petId=" + pet))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
    call(a, get("/api/v1/postcards/" + id + "/image?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"))
        .andExpect(content().bytes(PNG));
  }

  @Test
  @DisplayName("#103-S5 생성이 실패하면 FAILED가 되고 회원당 횟수만 돌려받는다")
  void failureRefundsMember(CapturedOutput output) throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    postcard(a, pet, diarySource(diary));
    postcard(a, pet, diarySource(diary));
    returnsPng();
    drain();
    long third = postcard(a, pet, diarySource(diary));
    doThrow(new PostcardGenerationFailedException("timeout key=SECRET-KEY body={\"error\":\"raw\"}"))
        .when(generator)
        .generate(any());
    long totalBefore = total();

    assertThat(worker.processNext()).isTrue();

    call(a, get("/api/v1/postcards/" + third + "?petId=" + pet))
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.errorMessage").value("엽서를 만들지 못했어요. 다시 요청해 주세요."))
        .andExpect(jsonPath("$.imageUrl").value(nullValue()));
    assertThat(used(a)).isEqualTo(2);
    assertThat(total()).isEqualTo(totalBefore);

    postcard(a, pet, diarySource(diary));
    assertThat(used(a)).isEqualTo(3);
    assertThat(total()).isEqualTo(totalBefore + 1);
    assertThat(output.getAll()).doesNotContain("SECRET-KEY", "\"raw\"");
    assertThat(Files.exists(ROOT.resolve("images/" + third + ".png"))).isFalse();
  }

  @Test
  @DisplayName("#103-S6 만드는 중 지운 엽서는 그림을 받아도 다시 나타나지 않고 파일도 남지 않는다")
  void deletedWhileProcessing() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    long id = postcard(a, pet, diarySource(diary));
    long usedBefore = used(a), totalBefore = total();
    doAnswer(
            i -> {
              call(a, delete("/api/v1/postcards/" + id + "?petId=" + pet))
                  .andExpect(status().isNoContent());
              return new GeneratedPostcardImage(PNG, "image/png");
            })
        .when(generator)
        .generate(any());

    worker.processNext();

    call(a, get("/api/v1/postcards/" + id + "?petId=" + pet)).andExpect(status().isNotFound());
    call(a, get("/api/v1/postcards?petId=" + pet)).andExpect(jsonPath("$.items").isEmpty());
    assertThat(Files.exists(ROOT.resolve("images/" + id + ".png"))).isFalse();
    assertThat(used(a)).isEqualTo(usedBefore);
    assertThat(total()).isEqualTo(totalBefore);
  }

  @Test
  @DisplayName("#103-S13 외부 호출 중 서버가 꺼진 엽서는 다시 부르지 않고, 만료는 한 번만 환불한다")
  void crashDuringCall() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long one = postcard(a, pet, diarySource(diary(a, pet, "첫 번째")));
    long two = postcard(a, pet, diarySource(diary(a, pet, "두 번째")));
    doThrow(new Crash()).when(generator).generate(any());
    assertThatThrownBy(() -> worker.processNext()).isInstanceOf(Crash.class);
    assertThat(
            jdbc.queryForObject(
                "select processing_started_at is not null from postcards where id=?",
                Boolean.class,
                one))
        .isTrue();

    reset(generator);
    returnsPng();
    drain();

    assertThat(generated()).extracting(PostcardImageRequest::content).containsExactly("두 번째");
    assertThat(stateOf(one)).isEqualTo("PROCESSING");
    assertThat(stateOf(two)).isEqualTo("COMPLETED");

    long usedBefore = used(a);
    clock.set(NOW.plus(java.time.Duration.ofMinutes(11)));
    var outcomes =
        race(List.of(() -> "" + worker.processNext(), () -> "" + worker.processNext()));

    assertThat(outcomes).hasSize(2);
    assertThat(stateOf(one)).isEqualTo("FAILED");
    assertThat(used(a)).isEqualTo(usedBefore - 1);
    assertThat(generated()).extracting(PostcardImageRequest::content).containsExactly("두 번째");
  }

  @Test
  @DisplayName("#103-S13 만료된 뒤 늦게 온 결과는 반영하지 않고 받은 이미지를 지운다")
  void lateResultAfterExpiry() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long id = postcard(a, pet, diarySource(diary(a, pet, "늦은 결과")));
    long usedBefore = used(a);
    doAnswer(
            i -> {
              // 호출이 길어지는 사이 다른 작업이 만료 처리를 끝냈다.
              transactions.fail(postcards.findById(id).orElseThrow());
              return new GeneratedPostcardImage(PNG, "image/png");
            })
        .when(generator)
        .generate(any());

    worker.processNext();

    assertThat(stateOf(id)).isEqualTo("FAILED");
    assertThat(used(a)).isEqualTo(usedBefore - 1);
    assertThat(Files.exists(ROOT.resolve("images/" + id + ".png"))).isFalse();
  }

  @Test
  @DisplayName("#103-S12 지운 일기의 엽서도 복사한 글과 첫 사진으로 만들고, 복사 사진은 남기지 않는다")
  void photosAfterDiaryDeleted() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long p21 = asset(a, pet, false), p22 = asset(a, pet, false), p23 = asset(a, pet, false);
    long withText = diary(a, pet, "벤치에서 쉬었다", p21, p22);
    long photoOnly = diary(a, pet, "", p23);
    long first = postcard(a, pet, diarySource(withText));
    long second = postcard(a, pet, diarySource(photoOnly));
    deleteDiaries.delete(new DeleteDiaryEntryCommand(a, pet, withText, 0L));
    deleteDiaries.delete(new DeleteDiaryEntryCommand(a, pet, photoOnly, 0L));
    returnsPng();

    drain();

    var sent = generated();
    assertThat(sent).hasSize(2);
    assertThat(sent.get(0).content()).isEqualTo("벤치에서 쉬었다");
    assertThat(new String(sent.get(0).photoJpeg())).isEqualTo("jpeg:" + storageKey(p21));
    assertThat(sent.get(1).content()).isEmpty();
    assertThat(new String(sent.get(1).photoJpeg())).isEqualTo("jpeg:" + storageKey(p23));
    assertThat(stateOf(first)).isEqualTo("COMPLETED");
    assertThat(stateOf(second)).isEqualTo("COMPLETED");
    call(a, get("/api/v1/postcards/" + second + "?petId=" + pet)).andExpect(jsonPath("$.text").value(""));
    assertThat(Files.exists(ROOT.resolve("sources/" + first + ".jpg"))).isFalse();
    assertThat(Files.exists(ROOT.resolve("sources/" + second + ".jpg"))).isFalse();
  }

  @Test
  @DisplayName("#103-S15 생성기는 설명과 요청 때의 종·품종을 받는다")
  void noteAndBreedAtRequest() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long voice = diary(a, pet, "", asset(a, pet, true));
    long text = diary(a, pet, "산책길에서 만난 친구");
    String note = "공원 벤치 옆에서 낙엽을 밟으며 놀았다";
    postcard(a, pet, withNote(voice, note));
    postcard(a, pet, withNote(text, note));
    editPets.edit(new EditPetCommand(a, pet, "콩이", "DOG", "푸들", null));
    returnsPng();

    drain();

    var sent = generated();
    assertThat(sent).hasSize(2);
    assertThat(sent.get(0).content()).isEmpty();
    assertThat(sent.get(0).note()).isEqualTo(note);
    assertThat(sent.get(1).content()).isEqualTo("산책길에서 만난 친구");
    assertThat(sent.get(1).note()).isEqualTo(note);
    assertThat(sent).extracting(PostcardImageRequest::breed).containsOnly("말티즈");
    assertThat(sent).extracting(PostcardImageRequest::species).containsOnly("DOG");
  }
}
