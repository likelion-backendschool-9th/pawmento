package com.pawmento.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;

@Import({TestcontainersConfiguration.class, PetBirthYearScenarioTest.TimeConfiguration.class})
@SpringBootTest(properties = {"pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
    "pawmento.media.worker.enabled=false", "pawmento.postcard.worker.enabled=false"})
@AutoConfigureMockMvc
class PetBirthYearScenarioTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.pawmento.pet.application.port.in.UpdatePetProfilePhotoUseCase photos;
    @Autowired TestClock clock;
    private static final long OWNER = 17801L;

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary TestClock birthYearClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>();
        void at(String value) { time.set(Instant.parse(value)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        public Instant instant() { return time.get(); }
    }
    @BeforeEach void resetClock() { clock.at("2026-11-24T00:00:00Z"); }

    @Test @DisplayName("#178-S1 연도만 등록한 뒤 목록·상세와 모드 변경 응답에서 연도와 나이를 반환한다")
    void yearOnlyPersistsAcrossAllResponses() throws Exception {
        long id = create("\"birthYear\":2020");
        view(id).andExpect(jsonPath("$.birthday").isEmpty()).andExpect(jsonPath("$.birthYear").value(2020))
            .andExpect(jsonPath("$.ageByYear").value(6));
        mvc.perform(get("/api/v1/pets").with(jwt().jwt(t -> t.subject("" + OWNER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].birthYear").value(org.hamcrest.Matchers.hasItem(2020)));
        mvc.perform(patch("/api/v1/pets/" + id + "/mode").with(jwt().jwt(t -> t.subject("" + OWNER)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"GARDEN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.birthYear").value(2020))
            .andExpect(jsonPath("$.ageByYear").value(6));
        var photoResult = photos.update(new com.pawmento.pet.application.port.in.UpdatePetProfilePhotoUseCase.Command(OWNER, id, null));
        assertThat(photoResult.birthYear()).isEqualTo(2020);
        assertThat(photoResult.ageByYear()).isEqualTo(6);
        assertThat(jdbc.queryForObject("select birth_year from pets where id = ?", Integer.class, id)).isEqualTo(2020);
    }

    @Test @DisplayName("#178-S2 한국 연말 자정에서 날짜·연도 전용 나이는 바뀌고 DB와 버전은 그대로다")
    void seoulYearBoundaryDoesNotWrite() throws Exception {
        long year = create("\"birthYear\":2020");
        long date = create("\"birthday\":\"2020-12-31\"");
        var before = jdbc.queryForList("select id, birthday, birth_year, version from pets where id in (?, ?) order by id", year, date);
        clock.at("2026-12-31T14:59:59Z");
        view(year).andExpect(jsonPath("$.ageByYear").value(6));
        view(date).andExpect(jsonPath("$.ageByYear").value(6));
        clock.at("2026-12-31T15:00:00Z");
        view(year).andExpect(jsonPath("$.ageByYear").value(7));
        view(date).andExpect(jsonPath("$.ageByYear").value(7));
        assertThat(jdbc.queryForList("select id, birthday, birth_year, version from pets where id in (?, ?) order by id", year, date)).isEqualTo(before);
    }

    @Test @DisplayName("#178-S3 기존 날짜·출생 정보 없음·1900년 이전 날짜 계약을 보존한다")
    void oldDateAndUnknownAreCompatible() throws Exception {
        long date = create("\"birthday\":\"1899-12-31\"");
        view(date).andExpect(jsonPath("$.birthday").value("1899-12-31"))
            .andExpect(jsonPath("$.birthYear").value(1899)).andExpect(jsonPath("$.ageByYear").value(127));
        change(date, "{\"birthday\":\"1898-05-12\"}").andExpect(status().isOk()).andExpect(jsonPath("$.birthYear").value(1898));
        long unknown = create("\"birthYear\":null");
        view(unknown).andExpect(jsonPath("$.birthday").isEmpty()).andExpect(jsonPath("$.birthYear").isEmpty())
            .andExpect(jsonPath("$.ageByYear").isEmpty());
        assertThat(jdbc.queryForObject("select birth_year from pets where id = ?", Integer.class, date)).isNull();
    }

    @Test @DisplayName("#178-S4 같은 연도는 날짜를 보존하고 다른 연도는 생일 null 여부와 무관하게 전환한다")
    void editsPreserveOrSwitchDate() throws Exception {
        for (String extra : List.of("", ",\"birthday\":null")) {
            long id = create("\"birthday\":\"2020-05-12\"");
            change(id, "{\"name\":\"새이름\",\"birthYear\":2020" + extra + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.birthday").value("2020-05-12"));
            change(id, "{\"birthYear\":2021" + extra + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.birthday").isEmpty()).andExpect(jsonPath("$.birthYear").value(2021));
            change(id, "{\"birthday\":\"2022-03-04\",\"birthYear\":2022}").andExpect(status().isOk());
            change(id, "{\"birthday\":null,\"birthYear\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.birthday").value("2022-03-04"));
            change(id, "{\"name\":\"이름만\"}").andExpect(status().isOk());
            view(id).andExpect(jsonPath("$.birthday").value("2022-03-04"));
            assertThat(jdbc.queryForObject("select birth_year from pets where id = ?", Integer.class, id)).isNull();
            mvc.perform(patch("/api/v1/pets/" + id).with(jwt().jwt(t -> t.subject("17802")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"birthYear\":2023}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test @DisplayName("#178-S5 잘못된 JSON 연도·범위·날짜는 등록·수정 모두 거절하고 모든 기존 필드를 보존한다")
    void badInputCannotCreateOrMutate() throws Exception {
        long id = create("\"birthday\":\"2020-05-12\"");
        var original = jdbc.queryForMap("select * from pets where id = ?", id);
        long count = jdbc.queryForObject("select count(*) from pets", Long.class);
        var fields = new java.util.ArrayList<String>();
        for (String value : List.of("-1", "0", "1899", "2027", "true", "[2020]", "{}", "\"2020\"", "2020.0", "2020.5", "1e3", "2147483648", "-2147483649")) {
            fields.add("\"birthYear\":" + value);
        }
        fields.addAll(List.of("\"birthday\":\"2026-02-30\"", "\"birthday\":\"2026-11-25\"",
            "\"birthday\":\"2020-05-12\",\"birthYear\":2021"));
        for (String field : fields) {
            mvc.perform(post("/api/v1/pets").with(jwt().jwt(t -> t.subject("" + OWNER)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"거절\",\"species\":\"DOG\"," + field + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
            change(id, "{\"name\":\"덮어쓰기\",\"breed\":\"바뀐품종\"," + field + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
            assertThat(jdbc.queryForMap("select * from pets where id = ?", id)).isEqualTo(original);
            assertThat(jdbc.queryForObject("select count(*) from pets", Long.class)).isEqualTo(count);
        }
    }

    @Test @DisplayName("#178-S5 연도 경계 1900과 올해는 허용한다")
    void validYearBounds() throws Exception {
        view(create("\"birthYear\":1900")).andExpect(jsonPath("$.ageByYear").value(126));
        view(create("\"birthYear\":2026")).andExpect(jsonPath("$.ageByYear").value(0));
    }

    long create(String fields) throws Exception {
        var result = mvc.perform(post("/api/v1/pets").with(jwt().jwt(t -> t.subject("" + OWNER)))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"보리\",\"species\":\"DOG\"," + fields + "}"))
            .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.petId")).longValue();
    }
    ResultActions view(long id) throws Exception {
        return mvc.perform(get("/api/v1/pets/" + id).with(jwt().jwt(t -> t.subject("" + OWNER)))).andExpect(status().isOk());
    }
    ResultActions change(long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/pets/" + id).with(jwt().jwt(t -> t.subject("" + OWNER)))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
