import { initialLifeState } from "../../src/life/adapters/outbound/BrowserLifeRepository.ts";
export function legacyQuotaFixture() {
  const state = initialLifeState();
  const life = JSON.stringify({
    ...state,
    questions: state.questions.map(({ id, text, enabled }) => ({
      id,
      text,
      enabled,
    })),
  });
  const pet = {
    id: "legacy-pet",
    name: "보리",
    species: "DOG",
    breed: "코기",
    birthday: "",
    photoUrl: "",
    mode: "ACTIVE",
  };
  const workspace = JSON.stringify({
    pets: [pet],
    selectedPetId: pet.id,
    records: [0, 1].map((i) => ({
      id: `legacy-voice-${i}`,
      petId: pet.id,
      authorId: "demo",
      type: "VOICE",
      body: "기존 음성 일기",
      question: "",
      photoUrl: "",
      recordedDate: "2026-11-15",
      createdAt: "2026-11-15T00:00:00Z",
      updatedAt: "2026-11-15T00:00:00Z",
      attachments: [
        {
          id: `voice-${i}`,
          name: "목소리.webm",
          type: "VOICE",
          mimeType: "audio/webm",
          url: "data:audio/webm;base64," + "A".repeat(1024 * 1024),
        },
      ],
    })),
  });
  const data: { [key: string]: string } = {
    "pawmento.life.v1": life,
    "pawmento.workspace.v1.demo": workspace,
  };
  const limit = 5 * 1024 * 1024;
  const used = Object.entries(data).reduce(
    (n, [k, v]) => n + 2 * (k.length + v.length),
    0,
  );
  const fillerKey = "quota-fixture";
  data[fillerKey] = "x".repeat((limit - 512 - used) / 2 - fillerKey.length);
  return { data, limit };
}
