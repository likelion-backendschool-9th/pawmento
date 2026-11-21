package com.pawmento.postcard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.postcard.application.port.in.RequestPostcardCommand;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

class PostcardPetDeletionScenarioTest extends PostcardScenarioSupport {
  @Autowired DeletePetUseCase deletePets;
  @Autowired DeleteDiaryEntryUseCase deleteDiaries;

  long petRows(long pet) {
    return jdbc.queryForObject("select count(*) from pets where id=?", Long.class, pet);
  }

  @Test
  @DisplayName("#103-S10 일기를 모두 지워도 엽서가 남아 있으면 반려동물을 지울 수 없다")
  void postcardBlocksPetDeletion() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());
    deleteDiaries.delete(new DeleteDiaryEntryCommand(a, pet, diary, 0L));

    call(a, delete("/api/v1/pets/" + pet))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    assertThat(petRows(pet)).isOne();
    assertThat(rows(pet)).isOne();
  }

  // 생성 요청이 일기를 복사한 뒤 엽서를 저장하기 직전에 멈추게 한다.
  Future<String> pausedRequest(long a, long pet, long diary, CountDownLatch paused, CountDownLatch go)
      throws Exception {
    doAnswer(
            i -> {
              paused.countDown();
              assertThat(go.await(30, TimeUnit.SECONDS)).isTrue();
              return i.callRealMethod();
            })
        .when(savePostcards)
        .save(any());
    var pool = Executors.newSingleThreadExecutor();
    var result =
        pool.submit(
            () -> {
              try {
                requestUseCase.request(new RequestPostcardCommand(a, pet, "DIARY", diary, null, true));
                return "accepted";
              } catch (RuntimeException e) {
                return e.getClass().getSimpleName();
              }
            });
    pool.shutdown();
    assertThat(paused.await(30, TimeUnit.SECONDS)).isTrue();
    return result;
  }

  @Test
  @DisplayName("#103-S11 엽서가 먼저 저장되면 삭제 확인을 통과한 반려동물 삭제도 외래 키로 409다")
  void postcardCommitsFirst() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    var paused = new CountDownLatch(1);
    var go = new CountDownLatch(1);
    var request = pausedRequest(a, pet, diary, paused, go);
    deleteDiaries.delete(new DeleteDiaryEntryCommand(a, pet, diary, 0L));
    go.countDown();
    assertThat(request.get(30, TimeUnit.SECONDS)).isEqualTo("accepted");
    // 엽서 커밋 전에 기록 확인을 통과한 삭제를 흉내 낸다.
    doReturn(false).when(petRecords).existsByPetId(pet);

    assertThatThrownBy(() -> deletePets.delete(new DeletePetCommand(a, pet)))
        .isInstanceOf(PetHasRecordsException.class);
    assertThat(petRows(pet)).isOne();
    assertThat(rows(pet)).isOne();
  }

  @Test
  @DisplayName("#103-S11 반려동물 삭제가 먼저 커밋되면 멈춰 있던 생성 요청은 404이고 횟수가 늘지 않는다")
  void petDeletionCommitsFirst() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    long totalBefore = total();
    var paused = new CountDownLatch(1);
    var go = new CountDownLatch(1);
    var request = pausedRequest(a, pet, diary, paused, go);
    deleteDiaries.delete(new DeleteDiaryEntryCommand(a, pet, diary, 0L));
    deletePets.delete(new DeletePetCommand(a, pet));
    go.countDown();

    assertThat(request.get(30, TimeUnit.SECONDS)).isEqualTo("PostcardPetNotFoundException");
    assertThat(rows(pet)).isZero();
    assertThat(used(a)).isZero();
    assertThat(total()).isEqualTo(totalBefore);
  }
}
