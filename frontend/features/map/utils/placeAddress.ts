import type { Place } from "../types/Place.ts";

// 장소 카드의 가려진 주소 보완(#239). 행정안전부 원본은 주소의 일부를 `*`로 가려서 보낸다.
// 가려진 주소의 장소를 열면 서버가 좌표로 찾은 도로명 주소를 받아 카드에 대신 보인다.

// 주소의 어느 위치든 `*`가 있으면 가려진 주소다(#239-S2). 없거나 비어 있으면 가려진 주소가 아니다.
export function isMaskedAddress(address: string | null): boolean {
  return address !== null && address.includes("*");
}

// 찾은 주소를 장소 id별로 기억한다. 화면은 자기 장소 id의 값만 읽으므로 다른 카드의 늦은 응답이 섞이지 않는다(#239-S10).
// 찾은 주소만 기억하고 실패는 기억하지 않아서, 같은 카드를 다시 열면 다시 시도한다(#239-S3).
// 이 화면에서만 쓰고 브라우저 저장소에는 남기지 않는다(#239-S4).
export class PlaceAddressResolver {
  private readonly found = new Map<string, string>();
  private readonly inFlight = new Set<string>();

  private readonly resolveAddress: (placeId: string) => Promise<string | null>;
  private readonly onChange: () => void;

  // 매개변수 속성(private x)은 node의 타입 제거 방식에서 쓸 수 없어 필드를 직접 적는다
  constructor(
    resolveAddress: (placeId: string) => Promise<string | null>,
    onChange: () => void,
  ) {
    this.resolveAddress = resolveAddress;
    this.onChange = onChange;
  }

  // 카드에 보일 주소. 찾았으면 찾은 주소, 아니면 원래 주소다.
  addressOf(place: Place): string | null {
    return this.found.get(place.id) ?? place.address;
  }

  // 가려진 주소이고 아직 찾지 않았고 찾는 중도 아니면 서버에 묻는다. 실패는 조용히 넘어가 원래 주소가 그대로 보인다.
  request(place: Place): void {
    if (!isMaskedAddress(place.address)) return; // #239-S2: 호출을 아낀다
    if (this.found.has(place.id) || this.inFlight.has(place.id)) return; // #239-S4
    this.inFlight.add(place.id);
    this.resolveAddress(place.id)
      .then((address) => {
        if (address === null) return;
        this.found.set(place.id, address);
        this.onChange();
      })
      .catch(() => {
        // 못 찾으면 원래 주소를 그대로 보인다(#239-S3). 오류 문구를 따로 보이지 않는다
      })
      .finally(() => {
        this.inFlight.delete(place.id);
      });
  }
}
