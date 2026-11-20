# 회원 계정 태그

관련 이슈: [#137](https://github.com/likelion-backendschool-9th/pawmento/issues/137)

## 한눈에
- 끝나면: 모든 회원이 숫자 4자리 태그를 갖고, 화면은 `닉네임#태그`(예: `다윗#0427`)로 같은 닉네임을 구분한다. 닉네임을 바꾸면 태그도 새로 뽑힌다.
- 결정 필요: 없음
- 규모: 4단계 (백엔드 PR 1, 프론트 PR 1)

## 1. 가입할 때 태그 (#137-R1)
이메일·소셜로 가입하면 숫자 4자리 태그를 받는다.

#137-S1 이메일 가입 [테스트]
- When  `POST /api/v1/auth/signup`에 `{"email":"dawit@example.com","password":"correct-password","nickname":"다윗"}`
- Then  201과 회원 정보에 `tag`가 있고, `0000`~`9999` 중 4자리 숫자 문자열이다(예: `"0427"`)

#137-S2 소셜 가입 [테스트]
- When  처음 구글 로그인으로 가입한다
- Then  회원 정보에 같은 형식의 `tag`가 있다

#137-S3 내 정보 [테스트]
- When  `GET /api/v1/members/me`
- Then  200과 `{"id":1,"email":"…","nickname":"다윗","tag":"0427","role":"USER"}`

## 2. 닉네임을 바꾸면 새 태그 (#137-R2)
닉네임이 실제로 바뀔 때만 태그를 새로 뽑는다.

#137-S4 닉네임 변경 [테스트]
- Given 회원이 `다윗#0427`이고 이번 달에 닉네임을 바꾼 적이 없다
- When  `PATCH /api/v1/members/me`에 `{"nickname":"보리 보호자"}`
- Then  200이고 `nickname`은 `보리 보호자`, `tag`는 4자리 숫자이며 `0427`이 아니다

#137-S5 같은 닉네임 [테스트]
- Given 회원이 `다윗#0427`이다
- When  같은 `다윗`으로 저장한다
- Then  200이고 태그는 `0427` 그대로다. 한 달 변경 한도도 쓰지 않는다

## 3. 조합은 하나뿐 (#137-R3)
같은 `닉네임#태그`는 두 회원이 가질 수 없다.

#137-S6 겹치면 다시 뽑기 [테스트]
- Given `다윗#0427`인 회원이 이미 있다
- When  다른 회원의 새 태그를 뽑는데 처음 나온 값이 `0427`이다
- Then  다시 뽑아 다른 값으로 저장한다
- Given 닉네임 변경에서 새로 뽑은 값이 이전 태그와 같다
- Then  역시 다시 뽑는다

#137-S7 닉네임 변경 저장 충돌 [테스트]
- Given 실제 PostgreSQL에 `보리#0427`인 회원 A가 있고, 회원 B가 `다윗#1111`이다. 테스트용 태그 생성기가 `0427`, `0581` 순서로 돌려준다(겹침 확인 없이)
- When  B가 `PATCH /api/v1/members/me`에 `{"nickname":"보리"}`
- Then  첫 시도는 `uk_member_nickname_tag`에 걸려 롤백되고, 두 번째 시도에서 200과 `보리#0581`이다. A는 `보리#0427` 그대로다

## 4. 기존 회원과 화면 (#137-R4)

#137-S8 개발 서버 기존 회원 [명령]
- Given 태그 없이 가입한 회원이 n명 있다
- When  이전 SQL을 실행한다
- Then  n명 모두 4자리 태그가 있고, 겹치는 `(nickname, tag)`가 0줄이다

#137-S9 마이페이지 표시 [직접]
- When  로그인 후 마이페이지를 연다
- Then  닉네임 옆에 `#0427`이 보이고, 닉네임을 바꾸면 태그도 바뀐 값으로 보인다

#137-S10 가입 저장 충돌 [테스트]
- Given 실제 PostgreSQL에 `보리#0427`인 회원이 있고, 테스트용 태그 생성기가 `0427`, `0581` 순서로 돌려준다
- When  다른 이메일로 닉네임 `보리` 가입(이메일 가입, 소셜 첫 로그인 각각)
- Then  201(소셜은 200)과 `보리#0581`이고, 회원은 1명만 새로 생긴다

#137-S11 재시도를 다 써도 원래대로 [테스트]
- Given 실제 PostgreSQL에 `보리#0427`인 회원 A가 있고, 회원 B가 `다윗#1111`이며 이번 달에 닉네임을 바꾼 적이 없다. 테스트용 태그 생성기가 계속 `0427`만 돌려준다
- When  B가 `{"nickname":"보리"}`로 바꾼다
- Then  3번 모두 충돌해 409이고, B는 닉네임 `다윗`, 태그 `1111`, 닉네임 변경 시각까지 요청 전과 같다. B는 이번 달에 다시 바꿀 수 있다

## 범위 밖
- 태그로 회원 검색, 태그 직접 고르기
- 가족 목록의 태그 표시: 가족 계정(#133)에서 이 태그를 쓴다

## 작업 순서
1. 도메인과 저장 — S1~S3, S6, S10
   - 파일: `member/domain/Member.java`(`tag` 필드, `register`·`registerSocial`이 태그를 받음, 4자리 숫자 검사), 새 `application/service/MemberTagGenerator.java`(`PetTagGenerator` 방식), `ExistsMemberPort`에 `existsByNicknameAndTag`, `MemberJpaEntity.java`(`tag` 컬럼, `uk_member_nickname_tag`), `MemberResult`·`MemberResponse`에 `tag`, `SignUpMemberService`·`SocialLoginMemberService`
2. 닉네임 변경 — S4, S5, S7, S11
   - 파일: `Member.changeNickname`이 새 태그를 받음, `ChangeMemberNicknameService`, `MemberPersistenceAdapter`(제약 이름으로 `DuplicateMemberTagException`), 새 `application/exception/DuplicateMemberTagException.java`(StateConflict)
3. 개발 서버 이전 — S8
   - #109 PR 1의 [개발 서버 이전 절차](social-account-link.md#개발-서버-이전-절차)와 같은 중지 시간에 함께 실행한다
4. 마이페이지 표시 — S9
   - 파일: 프론트 `auth/domain/account.ts`(`Account`에 `tag`), `HttpAuthRepository`(응답 매핑), 마이페이지 닉네임 표시

## 정한 것
- 태그는 숫자 4자리(`0000`~`9999`)다.
- 가입할 때(이메일·소셜) 받고, 닉네임을 바꿀 때마다 새로 뽑는다. 같은 닉네임으로 저장하면 그대로다.
- `닉네임#태그` 조합은 두 회원이 가질 수 없다. 겹치면 다시 뽑는다.
- 닉네임 변경에서 새 태그가 이전 태그와 같으면 다시 뽑는다.- 태그 충돌 재시도는 저장 트랜잭션 밖에서 한다. 서비스 메서드에는 `@Transactional`을 두지 않고, 한 번의 시도(회원 조회·닉네임과 한 달 한도 검사·태그 발급·저장)를 `TransactionTemplate`의 새 트랜잭션으로 실행한다. `uk_member_nickname_tag` 위반으로 진 시도는 통째로 롤백하고, 다음 시도는 회원을 다시 읽어 처음부터 한다. 최대 3번 시도하고, 다 지면 409(`DuplicateMemberTagException`)다.
- 적용 범위: 이메일 가입과 닉네임 변경은 위 방식으로 바꾼다. 소셜 첫 로그인은 이미 메서드 전체 트랜잭션이 없어 `save` 호출마다 따로 커밋되므로 반복문에 태그 충돌만 추가한다. 이메일·소셜 계정 충돌은 지금처럼 재시도 없이 기존 처리(409 또는 다시 조회)를 따른다.

## 가정
- 태그는 숫자지만 앞자리 0을 지키려고 문자열(`varchar(4)`)로 저장한다. `0042`가 `42`가 되지 않게 하기 위해서다.
- 뽑기는 `SecureRandom`으로 하고, 겹침을 확인하며 최대 32번 다시 뽑는다(`PetTagGenerator`와 같음). 같은 닉네임이 1만 명을 넘어 자리가 없으면 서버 오류로 둔다.
- 응답은 `nickname`과 `tag`를 따로 준다. `#`로 붙이는 것은 화면이 한다.
- 닉네임 변경 한 달 한도는 그대로라, 태그도 최대 한 달에 한 번 바뀐다.

## 참고
- `ddl-auto: update`는 행이 있는 테이블에 NOT NULL 컬럼을 추가하지 못한다. 개발 서버는 SQL로 먼저 채운다.
  ```sql
  ALTER TABLE member ADD COLUMN tag varchar(4);
  UPDATE member SET tag = lpad(floor(random() * 10000)::int::text, 4, '0');
  -- 같은 (닉네임, 태그)가 있으면 그 행만 다시 채우고, 0줄이 될 때까지 반복한다
  SELECT nickname, tag, count(*) FROM member GROUP BY nickname, tag HAVING count(*) > 1;
  ALTER TABLE member ALTER COLUMN tag SET NOT NULL;
  ALTER TABLE member ADD CONSTRAINT uk_member_nickname_tag UNIQUE (nickname, tag);
  ```
- `lpad(값, 4, '0')`은 앞에 0을 채워 4자리로 만든다.
- #109 PR 1도 `member` 테이블과 `MemberJpaEntity`를 바꾼다. 두 PR은 차례로 머지하고, 개발 서버 이전은 한 번의 중지 시간에 함께 한다.