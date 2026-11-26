package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/map/place-search.md(#195-S2, S19)와 animal-places.md(#172-S15)에 있다.
// 이름 비교 규칙(comparableName)을 고정한다. 이 규칙은 겹침 제거뿐 아니라 저장된 검색 키(places.name_key·address_key)와 검색어 정규화를
// 함께 정한다. 이 테스트가 실패하면 규칙을 바꾼 것이다. 그 경우 이미 저장된 검색 키가 옛 규칙 그대로 남아 검색이 오류 없이 일부 장소를 놓친다.
// 규칙을 바꾸는 것이 맞다면 아래 기대값을 고치고, docs/map/place-search.md의 "정한 것"에 적은 절차대로 저장된 검색 키를 모두 다시 채워야 한다.
class PlaceSelectionComparableNameTest {

	@Test
	@DisplayName("#195-S2 이름 비교 규칙을 고정한다. 바꾸면 저장된 검색 키를 모두 다시 채워야 한다(docs/map/place-search.md)")
	void pinsTheComparisonRule() {
		// 띄어쓰기·탭·줄바꿈
		assertThat(PlaceSelection.comparableName("스테이 동물병원")).isEqualTo("스테이동물병원");
		assertThat(PlaceSelection.comparableName(" 스테이\t동물\n병원 ")).isEqualTo("스테이동물병원");
		// 괄호와 구두점. 하이픈·밑줄·점·쉼표·작은따옴표·큰따옴표·가운뎃점
		assertThat(PlaceSelection.comparableName("스테이(동물병원)")).isEqualTo("스테이동물병원");
		assertThat(PlaceSelection.comparableName("[서울]{숲}-동물_병원.1,2'3\"4·5")).isEqualTo("서울숲동물병원12345");
		// 대소문자
		assertThat(PlaceSelection.comparableName("Stay Clinic")).isEqualTo("stayclinic");
		// 지우지 않는 글자. 슬래시·퍼센트·느낌표·별표·콜론·앰퍼샌드는 남는다(검색어의 LIKE escape가 % 와 ! 를 다루는 이유)
		assertThat(PlaceSelection.comparableName("a/b%c!d*e:f&g")).isEqualTo("a/b%c!d*e:f&g");
		// 주소의 쉼표·괄호·층·호도 같은 규칙이다
		assertThat(PlaceSelection.comparableName("서울 성동구 서울숲*길 **-**, ***동 *층 ***호 (성수동*가, 갤러리아 포레)"))
			.isEqualTo("서울성동구서울숲*길*******동*층***호성수동*가갤러리아포레");
		// 소문자로 바꾸면 길어지는 글자(İ는 i와 결합 점 두 글자)
		assertThat(PlaceSelection.comparableName("İ")).isEqualTo("i̇");
		assertThat(PlaceSelection.comparableName("")).isEmpty();
	}

}
