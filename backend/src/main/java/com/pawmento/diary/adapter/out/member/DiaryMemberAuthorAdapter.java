package com.pawmento.diary.adapter.out.member;

import com.pawmento.diary.application.DiaryAuthorNames;
import com.pawmento.diary.application.exception.DiaryAuthorNotFoundException;
import com.pawmento.diary.application.exception.DiaryPersistenceUnavailableException;
import com.pawmento.diary.application.port.out.FindDiaryAuthorNamePort;
import com.pawmento.diary.application.port.out.LoadDiaryAuthorNamePort;
import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import java.util.Optional;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class DiaryMemberAuthorAdapter implements LoadDiaryAuthorNamePort, FindDiaryAuthorNamePort {
  private final GetMemberUseCase members;
  private final TransactionTemplate read;

  DiaryMemberAuthorAdapter(GetMemberUseCase members, PlatformTransactionManager manager) {
    this.members = members;
    read = new TransactionTemplate(manager);
    read.setReadOnly(true);
    read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public String loadName(Long id) {
    try {
      // 정상 작성은 호출자의 TX에 참여해 별도 DB 연결을 추가로 요구하지 않는다.
      return DiaryAuthorNames.stored(members.get(new GetMemberQuery(id)).nickname());
    } catch (MemberNotFoundException missing) {
      throw new DiaryAuthorNotFoundException(missing);
    } catch (DataAccessResourceFailureException | TransientDataAccessResourceException failure) {
      throw new DiaryPersistenceUnavailableException(failure);
    }
  }

  public Optional<String> findName(Long id) {
    try {
      // 이관에서만 회원 부재를 허용한다. 실패한 내부 TX가 끝난 뒤 빈 값으로 바꾼다.
      return Optional.of(read.execute(status -> loadName(id)));
    } catch (DiaryAuthorNotFoundException missing) {
      return Optional.empty();
    }
  }
}
