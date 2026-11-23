# 공통 앱 데모

여러 도메인을 묶는 브라우저 데모의 요구사항과 결정을 모은다. 화면/API 계층별로 계획을 나누지 않는다.

| 이슈 | 범위 | 문서 |
|---|---|---|
| [#12](https://github.com/likelion-backendschool-9th/pawmento/issues/12) | 계정·반려동물·질문·기록의 초기 계획 | [P1 계획](p1-implementation.md) |
| [#12](https://github.com/likelion-backendschool-9th/pawmento/issues/12) | 커뮤니티·정원·엽서·가족·운영을 포함한 공통 데모 | [전체 확장 계획](full-demo.md) |

구현의 표현 기준은 [헥사고날 청사진](../Frontend/PawMento_Frontend_Hexagonal_Blueprint.md)과 [디자인 가이드](../Frontend/PawMento_Design_Tokens_Hexagonal.md)를 참고한다.

## 프론트 구조 개편 시 참고 범위

사용자가 요청한 새 책임·폴더·목업 분리 기준은 [프론트 구조 개편](../frontend-structure/README.md)에서 검토한다. 이 폴더의 브라우저 저장·헥사고날 유스케이스·데모 출처 혼합은 이전 구현의 배경이며, 신규 운영 구조의 기준으로 복제하지 않는다. 화면 동작·데이터 호환·미완료 백엔드 범위는 개편 전환 계획에서 확인한다.
