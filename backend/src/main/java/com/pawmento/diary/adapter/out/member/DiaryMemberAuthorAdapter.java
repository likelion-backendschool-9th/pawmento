package com.pawmento.diary.adapter.out.member;

import com.pawmento.diary.application.exception.DiaryAuthorMemberNameInvalidException;
import com.pawmento.diary.application.exception.DiaryAuthorMemberNotFoundException;
import com.pawmento.diary.application.exception.DiaryPersistenceUnavailableException;
import com.pawmento.diary.application.port.out.LoadDiaryAuthorNameForBackfillPort;
import com.pawmento.diary.application.port.out.LoadDiaryAuthorNamePort;
import com.pawmento.diary.domain.DiaryAuthorName;
import com.pawmento.diary.domain.exception.InvalidDiaryAuthorNameException;
import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
class DiaryMemberAuthorAdapter
    implements LoadDiaryAuthorNamePort, LoadDiaryAuthorNameForBackfillPort {
  private final GetMemberUseCase members;
  private final PlatformTransactionManager manager;

  public DiaryAuthorName loadName(Long id) {
    String nickname;
    try {
      // 정상 작성은 호출자의 TX에 참여해 별도 DB 연결을 추가로 요구하지 않는다.
      nickname = members.get(new GetMemberQuery(id)).nickname();
    } catch (MemberNotFoundException missing) {
      throw new DiaryAuthorMemberNotFoundException(missing);
    } catch (DataAccessResourceFailureException | TransientDataAccessResourceException failure) {
      throw new DiaryPersistenceUnavailableException(failure);
    }
    try {
      return new DiaryAuthorName(nickname);
    } catch (InvalidDiaryAuthorNameException invalid) {
      throw new DiaryAuthorMemberNameInvalidException(invalid);
    }
  }

  public Optional<DiaryAuthorName> loadNameForBackfill(Long id) {
    var read = new TransactionTemplate(manager);
    read.setReadOnly(true);
    read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      // 이관에서만 회원 부재를 허용한다. 실패한 내부 TX가 끝난 뒤 빈 값으로 바꾼다.
      return Optional.of(read.execute(status -> loadName(id)));
    } catch (DiaryAuthorMemberNotFoundException missing) {
      return Optional.empty();
    }
  }
}
