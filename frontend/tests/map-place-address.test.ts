import test from "node:test";
import assert from "node:assert/strict";
import { createPlaceApi } from "../features/map/api/placeApi.ts";
import type { Place } from "../features/map/types/Place.ts";
import {
  isMaskedAddress,
  PlaceAddressResolver,
} from "../features/map/utils/placeAddress.ts";
import { DemoPlaceRepository } from "../mocks/handlers/DemoPlaceRepository.ts";
import { DEMO_PLACES } from "../mocks/fixtures/demoPlaces.ts";

// 시나리오는 docs/map/place-address.md(#239)에 있다. 화면의 가려진 주소 보완 규칙을 확인한다.

const MASKED = "서울특별시 성동구 서울숲*길 **-**, ***동 *층 ***호 (성수동*가, 갤러리아 포레)";
const FOUND = "서울특별시 성동구 서울숲2길 32-14 (갤러리아 포레)";

const place = (id: string, address: string | null): Place => ({
  ...DEMO_PLACES[0],
  id,
  address,
});

// 응답을 직접 풀어 줄 수 있는 가짜 서버. 요청 순서와 응답 순서를 다르게 만들 때 쓴다
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

test("#239-S2 주소의 어느 위치든 별표가 있으면 가려진 주소이고, 없거나 비어 있으면 아니다", () => {
  assert.equal(isMaskedAddress(MASKED), true);
  assert.equal(isMaskedAddress("서울 성동구 진흥로 ***"), true);
  assert.equal(isMaskedAddress("서울 종로구 대학로 1"), false);
  assert.equal(isMaskedAddress(""), false);
  assert.equal(isMaskedAddress(null), false);
});

test("#239-S1 가려진 주소를 찾으면 카드에는 찾은 주소가 보인다", async () => {
  let changes = 0;
  const resolver = new PlaceAddressResolver(
    async () => FOUND,
    () => changes++,
  );
  const target = place("603", MASKED);

  assert.equal(resolver.addressOf(target), MASKED); // 찾는 동안에는 원래 주소
  resolver.request(target);
  await settle();

  assert.equal(resolver.addressOf(target), FOUND);
  assert.equal(changes, 1);
});

test("#239-S2 가려지지 않은 주소와 주소가 없는 장소는 서버를 부르지 않는다", async () => {
  const asked: string[] = [];
  const resolver = new PlaceAddressResolver(
    async (id) => {
      asked.push(id);
      return FOUND;
    },
    () => {},
  );

  resolver.request(place("1", "서울 종로구 대학로 1"));
  resolver.request(place("2", null));
  await settle();

  assert.deepEqual(asked, []);
  assert.equal(resolver.addressOf(place("1", "서울 종로구 대학로 1")), "서울 종로구 대학로 1");
});

test("#239-S3 찾지 못하거나(null) 실패하면 원래 주소가 그대로 보이고 오류를 던지지 않는다", async () => {
  const resolver = new PlaceAddressResolver(
    async (id) => {
      if (id === "1") return null;
      throw new Error("서버 오류");
    },
    () => {},
  );
  const none = place("1", MASKED);
  const broken = place("2", MASKED);

  resolver.request(none);
  resolver.request(broken);
  await settle();

  assert.equal(resolver.addressOf(none), MASKED);
  assert.equal(resolver.addressOf(broken), MASKED);
});

test("#239-S3 실패한 장소는 다시 열면 다시 시도하고, 그때 찾으면 보인다", async () => {
  let calls = 0;
  const resolver = new PlaceAddressResolver(
    async () => {
      calls++;
      if (calls === 1) throw new Error("일시적인 오류");
      return FOUND;
    },
    () => {},
  );
  const target = place("603", MASKED);

  resolver.request(target);
  await settle();
  assert.equal(resolver.addressOf(target), MASKED);

  resolver.request(target); // 같은 카드를 다시 연다
  await settle();
  assert.equal(calls, 2);
  assert.equal(resolver.addressOf(target), FOUND);
});

test("#239-S4 이미 찾은 장소는 서버를 다시 부르지 않고, 찾는 중에 다시 열어도 겹쳐 부르지 않는다", async () => {
  let calls = 0;
  const pending = deferred<string | null>();
  const resolver = new PlaceAddressResolver(
    () => {
      calls++;
      return pending.promise;
    },
    () => {},
  );
  const target = place("603", MASKED);

  resolver.request(target);
  resolver.request(target); // 찾는 중에 다시 연다
  assert.equal(calls, 1);

  pending.resolve(FOUND);
  await settle();
  resolver.request(target); // 이미 찾았다
  assert.equal(calls, 1);
  assert.equal(resolver.addressOf(target), FOUND);
});

test("#239-S10 A를 찾는 중에 B로 바꾼 뒤 A의 응답이 늦게 와도 B의 주소는 바뀌지 않는다", async () => {
  const a = deferred<string | null>();
  const b = deferred<string | null>();
  const answers = new Map([
    ["A", a.promise],
    ["B", b.promise],
  ]);
  const resolver = new PlaceAddressResolver(
    (id) => answers.get(id) ?? Promise.resolve(null),
    () => {},
  );
  const placeA = place("A", "서울 성동구 서울숲*길 **");
  const placeB = place("B", "서울 성동구 왕십리로 **, *층 *호 (성수동*가)");

  resolver.request(placeA);
  resolver.request(placeB); // B를 열었다
  b.resolve("서울특별시 성동구 왕십리로 16");
  await settle();
  a.resolve("서울특별시 성동구 서울숲2길 32-14"); // A의 응답이 B 뒤에 도착한다
  await settle();

  assert.equal(resolver.addressOf(placeB), "서울특별시 성동구 왕십리로 16");
  assert.equal(resolver.addressOf(placeA), "서울특별시 성동구 서울숲2길 32-14"); // A로 돌아오면 A의 주소가 보인다
});

test("#239-S6 서버 API: 장소 id로 주소를 묻고 응답의 address를 돌려준다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi({
    get: async (path: string) => {
      seen.push(path);
      return { address: FOUND };
    },
  } as never);

  assert.equal(await api.resolveAddress("603"), FOUND);
  assert.deepEqual(seen, ["/maps/places/603/address"]);
});

test("#239-S6 서버 API: address가 null이거나 빈 글자이면 null이다", async () => {
  const empty = createPlaceApi({ get: async () => ({ address: null }) } as never);
  const blank = createPlaceApi({ get: async () => ({ address: "  " }) } as never);

  assert.equal(await empty.resolveAddress("603"), null);
  assert.equal(await blank.resolveAddress("603"), null);
});

test("#239-S8 서버의 장소 id가 아니면(예시 장소 등) 서버를 부르지 않는다", async () => {
  let calls = 0;
  const api = createPlaceApi({
    get: async () => {
      calls++;
      return { address: FOUND };
    },
  } as never);

  assert.equal(await api.resolveAddress("demo-place-1"), null);
  assert.equal(await api.resolveAddress("0"), null);
  assert.equal(await api.resolveAddress("-3"), null);
  assert.equal(await api.resolveAddress("12.5"), null);
  assert.equal(await api.resolveAddress(""), null);
  assert.equal(calls, 0);
});

test("#239-S8 개발용 예시 데이터는 서버를 부르지 않고 null이다", async () => {
  assert.equal(await new DemoPlaceRepository().resolveAddress(), null);
});
